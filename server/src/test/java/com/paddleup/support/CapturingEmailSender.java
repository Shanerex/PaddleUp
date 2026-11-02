package com.paddleup.support;

import com.paddleup.accounts.service.EmailSender;
import com.paddleup.accounts.service.OutgoingEmail;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test double that records outgoing sign-in emails instead of sending them. */
public class CapturingEmailSender implements EmailSender {

  private final List<OutgoingEmail> sent = new CopyOnWriteArrayList<>();

  @Override
  public void send(OutgoingEmail email) {
    sent.add(email);
  }

  public OutgoingEmail lastTo(String address) {
    return sent.reversed().stream()
        .filter(m -> m.to().equals(address))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no email sent to " + address));
  }

  public String tokenTo(String address) {
    Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(lastTo(address).text());
    if (!m.find()) throw new AssertionError("no link in email");
    return m.group(1);
  }

  public String codeTo(String address) {
    Matcher m = Pattern.compile("code: (\\d{6})").matcher(lastTo(address).text());
    if (!m.find()) throw new AssertionError("no code in email");
    return m.group(1);
  }
}
