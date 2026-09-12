package dev.w0fv1.norm.packages;

import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;

interface NormPackageRepository {
  java.util.Set<String> moduleNames(HttpClient client) throws IOException;

  URI locate(ModuleRequirement requirement, HttpClient client) throws IOException;

  int latestVersion(String moduleName, HttpClient client) throws IOException;
}
