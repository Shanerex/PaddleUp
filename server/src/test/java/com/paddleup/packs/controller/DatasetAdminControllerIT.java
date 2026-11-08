package com.paddleup.packs.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.packs.service.SeasonDatasetService;
import com.paddleup.support.AbstractApiIT;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;

/** AC-1, AC-2, AC-3, AC-4: the admin import endpoints. */
@TestPropertySource(properties = "paddleup.admin.emails=Season-Admin@Example.Test")
class DatasetAdminControllerIT extends AbstractApiIT {

  private static final String PACK = "cricket-t20";
  private static final String CSV =
      """
      name,country,role,tags,base_price,set,provider_id
      Ann Lee,Chile,BAT,capped;overseas,20,Marquee,p-1
      Bo Ray,Peru,BOWL,,50,Marquee,
      Cy Day,Chile,AR,uncapped,100,Capped Batters,
      """;
  private static final String JSON =
      """
      {"players":[
       {"name":"Ann Lee","country":"Chile","role":"BAT","tags":["capped","overseas"],"base_price":20,"set":"Marquee","provider_id":"p-1"},
       {"name":"Bo Ray","country":"Peru","role":"BOWL","tags":[],"base_price":50,"set":"Marquee"},
       {"name":"Cy Day","country":"Chile","role":"AR","tags":["uncapped"],"base_price":100,"set":"Capped Batters"}
      ]}
      """;

  @Autowired SeasonDatasetService datasets;
  @Autowired ObjectMapper json;

  // Sign-in is throttled per email, so the admin signs in once for the whole class.
  private static String adminCookie;

  private String adminCookie() {
    if (adminCookie == null) {
      adminCookie = signIn("season-admin@example.test");
    }
    return adminCookie;
  }

  private ResponseEntity<String> upload(
      String cookie, String packId, String fileName, byte[] bytes) {
    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("packId", packId);
    parts.add(
        "file",
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return fileName;
          }
        });
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    if (cookie != null) headers.add(HttpHeaders.COOKIE, cookie);
    return rest.exchange(
        "/api/admin/datasets", HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
  }

  private ResponseEntity<String> upload(String cookie, String fileName, String text) {
    return upload(cookie, PACK, fileName, text.getBytes(StandardCharsets.UTF_8));
  }

  private JsonNode body(ResponseEntity<String> r) throws Exception {
    return json.readTree(r.getBody());
  }

  private int latestVersion() {
    return datasets.latestSummary(PACK).map(d -> d.version()).orElse(0);
  }

  @Test
  void aNonAdminGets403OnEveryEndpointAndNoDatasetIsCreated() {
    String user = signIn(newEmail());
    int before = latestVersion();

    assertThat(upload(user, "p.csv", CSV).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(HttpMethod.GET, "/api/admin/datasets?packId=" + PACK, user, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(call(HttpMethod.GET, "/api/admin/datasets/packs", user, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(latestVersion()).isEqualTo(before);
  }

  @Test
  void anAnonymousCallerGets401() {
    assertThat(upload(null, "p.csv", CSV).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theAllowListedAccountImportsAndGets201WithAReportAndItAppearsInTheVersionList()
      throws Exception {
    String cookie = adminCookie();
    int before = latestVersion();

    var response = upload(cookie, "players.csv", CSV);

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
    assertThat(body(response).get("version").asInt()).isEqualTo(before + 1);
    assertThat(body(response).get("imported").asInt()).isEqualTo(3);
    var list = body(call(HttpMethod.GET, "/api/admin/datasets?packId=" + PACK, cookie, null));
    assertThat(list.get(0).get("version").asInt()).isEqualTo(before + 1);
    assertThat(list.get(0).get("playerCount").asInt()).isEqualTo(3);
    var packs = body(call(HttpMethod.GET, "/api/admin/datasets/packs", cookie, null));
    assertThat(packs.findValuesAsText("id")).contains(PACK);
  }

  @Test
  void aCsvAndItsEquivalentJsonGiveIdenticalPlayers() {
    String cookie = adminCookie();

    assertThat(upload(cookie, "p.csv", CSV).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var fromCsv = datasets.latest(PACK).orElseThrow();
    assertThat(upload(cookie, "p.json", JSON).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var fromJson = datasets.latest(PACK).orElseThrow();

    assertThat(fromJson.version()).isEqualTo(fromCsv.version() + 1);
    assertThat(fromJson.setOrder()).isEqualTo(fromCsv.setOrder());
    assertThat(fromJson.players())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .isEqualTo(fromCsv.players());
  }

  @Test
  void aFileWithZeroValidRowsGives422WithTheReportAndNoNewVersion() throws Exception {
    String cookie = adminCookie();
    upload(cookie, "ok.csv", CSV);
    int before = latestVersion();

    var response =
        upload(
            cookie,
            "bad.csv",
            "name,country,role,tags,base_price,set\nA,Chile,NOPE,,20,S\nB,Chile,BAT,nope,20,S\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    var report = body(response).get("report");
    assertThat(body(response).get("error").asText()).isEqualTo("no_valid_rows");
    assertThat(report.get("skipped").asInt()).isEqualTo(2);
    assertThat(report.get("rows").get(0).get("field").asText()).isEqualTo("role");
    assertThat(report.get("rows").get(0).get("row").asInt()).isEqualTo(1);
    assertThat(latestVersion()).isEqualTo(before);
  }

  @Test
  void aUnreadableOrUnknownFileAndAnUnknownPackAreClientErrors() throws Exception {
    String cookie = adminCookie();

    var notData = upload(cookie, "p.txt", "hello");
    var broken = upload(cookie, "p.json", "{not json");
    var pack = upload(cookie, "no-such-pack", "p.csv", CSV.getBytes(StandardCharsets.UTF_8));

    assertThat(notData.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(notData).get("error").asText()).isEqualTo("invalid_file");
    assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(pack.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aFileOverTheOneMegabyteCapIsRefusedAndNothingIsStored() {
    String cookie = adminCookie();
    int before = latestVersion();

    var response = upload(cookie, PACK, "big.csv", new byte[1024 * 1024 + 10]);

    assertThat(response.getStatusCode())
        .as(response.getBody())
        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(latestVersion()).isEqualTo(before);
  }
}
