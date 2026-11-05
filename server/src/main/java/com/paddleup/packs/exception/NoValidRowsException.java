package com.paddleup.packs.exception;

import com.paddleup.packs.dto.ImportReportDto;

/** Every row of an import was rejected; nothing was stored. Maps to 422 with the report. */
public class NoValidRowsException extends RuntimeException {

  private final transient ImportReportDto report;

  public NoValidRowsException(ImportReportDto report) {
    super("No valid rows in the file");
    this.report = report;
  }

  public ImportReportDto report() {
    return report;
  }
}
