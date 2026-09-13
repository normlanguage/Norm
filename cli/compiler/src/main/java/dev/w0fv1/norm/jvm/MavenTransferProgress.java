package dev.w0fv1.norm.jvm;

import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;

final class MavenTransferProgress extends AbstractTransferListener {
  private final Consumer<String> progress;

  MavenTransferProgress(Consumer<String> progress) {
    this.progress = Objects.requireNonNull(progress, "progress");
  }

  @Override
  public void transferInitiated(TransferEvent event) {
    var resource = event.getResource();
    progress.accept("Downloading " + resource.getRepositoryUrl() + resource.getResourceName());
  }

  @Override
  public void transferSucceeded(TransferEvent event) {
    progress.accept(
        "Downloaded "
            + event.getResource().getResourceName()
            + " ("
            + event.getTransferredBytes()
            + " bytes)");
  }

  @Override
  public void transferFailed(TransferEvent event) {
    progress.accept(
        "Download failed: "
            + event.getResource().getResourceName()
            + ": "
            + event.getException().getMessage());
  }
}
