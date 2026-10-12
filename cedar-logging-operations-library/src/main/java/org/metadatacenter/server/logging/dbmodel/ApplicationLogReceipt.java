package org.metadatacenter.server.logging.dbmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.server.logging.model.AppLogSubType;

/** Compact durable replay fence. Kept after raw-log pruning; committed with the log mutation. */
@Entity
@Table(name = "log_event_receipt")
public class ApplicationLogReceipt {
  @Id @Column(length = 64)
  private String eventKey;
  private boolean processed;

  private int requestPhases;

  public boolean hasRequestFragments() { return requestPhases != 0; }

  public boolean isProcessed(AppLogMessage message) {
    return message.getType() == AppLogType.CYPHER_QUERY ? processed : (requestPhases & phase(message)) != 0;
  }

  public void markProcessed(AppLogMessage message) {
    if (message.getType() == AppLogType.CYPHER_QUERY) processed = true;
    else requestPhases |= phase(message);
  }

  private static int phase(AppLogMessage message) {
    if (message.getType() == AppLogType.REQUEST_FILTER) {
      if (message.getSubType() == AppLogSubType.START) return 1;
      if (message.getSubType() == AppLogSubType.END) return 2;
    }
    if (message.getType() == AppLogType.REQUEST_HANDLER) return 4;
    if (message.getType() == AppLogType.RESPONSE_EXCEPTION) return 8;
    throw new IllegalArgumentException("Unsupported request log phase");
  }
}
