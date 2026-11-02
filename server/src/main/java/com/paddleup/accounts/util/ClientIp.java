package com.paddleup.accounts.util;

/** Works out the client address behind a known number of trusted proxies. */
public final class ClientIp {

  private ClientIp() {}

  /**
   * With no trusted proxy the socket peer is the client. With {@code trustedHops} = n the client is
   * the nth entry from the right of {@code X-Forwarded-For}: each trusted proxy appends the address
   * it saw, and entries to the left of those are client-supplied and ignored. If the header is
   * missing or has fewer entries, the socket peer is used.
   */
  public static String resolve(String remoteAddr, String forwardedFor, int trustedHops) {
    if (trustedHops <= 0 || forwardedFor == null || forwardedFor.isBlank()) {
      return remoteAddr;
    }
    String[] parts = forwardedFor.split(",");
    int index = parts.length - trustedHops;
    if (index < 0) {
      return remoteAddr;
    }
    String ip = parts[index].trim();
    return ip.isEmpty() ? remoteAddr : ip;
  }
}
