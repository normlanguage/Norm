package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;

interface NormPackageRepository {
  URI locate(ModuleRequirement requirement, HttpClient client) throws IOException;

  int latestVersion(String moduleName, HttpClient client) throws IOException;
}
