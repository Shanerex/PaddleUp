package com.paddleup.packs.controller;

import com.paddleup.accounts.service.AdminAllowList;
import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.packs.dto.DatasetPackDto;
import com.paddleup.packs.dto.DatasetSummaryDto;
import com.paddleup.packs.dto.ImportReportDto;
import com.paddleup.packs.exception.DatasetFileException;
import com.paddleup.packs.exception.DatasetFileException.Reason;
import com.paddleup.packs.exception.NotAdminException;
import com.paddleup.packs.service.PackService;
import com.paddleup.packs.service.SeasonDatasetService;
import com.paddleup.packs.util.DatasetFileReader;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Season data import for app admins. Every endpoint is 403 unless the caller is on the list. */
@RestController
@RequestMapping("/api/admin/datasets")
class DatasetAdminController {

  private final CurrentUser currentUser;
  private final AdminAllowList admins;
  private final PackService packs;
  private final SeasonDatasetService datasets;

  DatasetAdminController(
      CurrentUser currentUser,
      AdminAllowList admins,
      PackService packs,
      SeasonDatasetService datasets) {
    this.currentUser = currentUser;
    this.admins = admins;
    this.packs = packs;
    this.datasets = datasets;
  }

  private com.paddleup.accounts.dto.UserId admin() {
    var user = currentUser.require();
    if (!admins.isAdmin(user)) {
      throw new NotAdminException();
    }
    return user;
  }

  /** The packs a file can be imported for, with their role and tag keys. */
  @GetMapping("/packs")
  List<DatasetPackDto> packs() {
    admin();
    return packs.list().stream()
        .map(s -> packs.get(s.id()))
        .map(
            p ->
                new DatasetPackDto(
                    p.id(),
                    p.version(),
                    p.display().name() == null || p.display().name().isBlank()
                        ? p.id()
                        : p.display().name(),
                    p.display().icon(),
                    p.roles(),
                    p.tags()))
        .toList();
  }

  @GetMapping
  List<DatasetSummaryDto> versions(@RequestParam String packId) {
    admin();
    return datasets.list(packId);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  ImportReportDto importFile(@RequestParam String packId, @RequestParam MultipartFile file) {
    var user = admin();
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new DatasetFileException(Reason.MALFORMED, "The file could not be read");
    }
    var format = DatasetFileReader.Format.detect(file.getOriginalFilename(), file.getContentType());
    return datasets.importFile(user, packId, format, bytes);
  }
}
