package dev.w0fv1.norm.jvm;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableSerializer;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public record PublishedJarBinding(
    ModuleDescriptor descriptor,
    Sha256Digest graphId,
    JarApiSchema api,
    GeneratedJarBinding generated,
    Map<String, JarBindingClassReference.Nominal> imports) {
  public static final String ABI = "norm-java-binding-5";
  public static final String ENTRY = "binding/prepared.bin";

  public PublishedJarBinding {
    imports = Map.copyOf(imports);
  }

  public static byte[] encode(ModuleDescriptor descriptor, ResolvedJarBinding binding)
      throws IOException {
    return PortableObjectCodec.encodeDeterministic(
        new PublishedJarBinding(
            descriptor,
            binding.graph().contentId(),
            binding.api(),
            binding.generated(),
            binding.imports()));
  }

  public static PublishedJarBinding decode(
      byte[] bytes, ModuleDescriptor descriptor, Sha256Digest apiId) throws IOException {
    return decode(bytes, descriptor, apiId, ABI);
  }

  public static boolean isReadable(String abi) {
    return ABI.equals(abi) || "norm-java-binding-4".equals(abi);
  }

  public static PublishedJarBinding decode(
      byte[] bytes, ModuleDescriptor descriptor, Sha256Digest apiId, String abi)
      throws IOException {
    if (!isReadable(abi)) throw new IOException("unsupported published Java binding ABI");
    var binding =
        PortableObjectCodec.decodeDeterministic(
            bytes,
            PublishedJarBinding.class,
            "norm-java-binding-4".equals(abi)
                ? Map.of(JarBindingType.class, new JarBindingTypeV4Serializer())
                : Map.of());
    if (!binding.descriptor().equals(descriptor) || !binding.api().apiId().equals(apiId))
      throw new IOException("published Java binding does not match its module manifest");
    return binding;
  }

  public ResolvedJarBinding link(ResolvedJarGraph graph) throws IOException {
    if (!graphId.equals(graph.contentId()))
      throw new IOException("published Java binding does not match its resolved dependency graph");
    return new ResolvedJarBinding(graph, api, generated, imports);
  }

  private static final class JarBindingTypeV4Serializer
      extends ImmutableSerializer<JarBindingType> {
    @Override
    public void write(Kryo kryo, Output output, JarBindingType value) {
      throw new UnsupportedOperationException("published Java binding version 4 is read-only");
    }

    @Override
    public JarBindingType read(Kryo kryo, Input input, Class<? extends JarBindingType> type) {
      List<String> borrowed =
          ((List<?>) kryo.readClassAndObject(input)).stream().map(String.class::cast).toList();
      List<String> members =
          ((List<?>) kryo.readClassAndObject(input)).stream().map(String.class::cast).toList();
      String name = kryo.readObjectOrNull(input, String.class);
      List<JarBindingOverload> overloads =
          ((List<?>) kryo.readClassAndObject(input))
              .stream().map(JarBindingOverload.class::cast).toList();
      return new JarBindingType(name, members, overloads, borrowed);
    }
  }
}
