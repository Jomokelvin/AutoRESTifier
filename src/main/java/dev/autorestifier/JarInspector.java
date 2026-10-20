package dev.autorestifier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarInspector {
    public List<Map<String, Object>> inspect(Path jarPath) throws IOException {
        List<Map<String, Object>> classes = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")
                        || entry.getName().startsWith("META-INF/versions/")) {
                    continue;
                }
                try (var input = jar.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(input);
                    ClassData data = new ClassData();
                    reader.accept(data, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (data.className != null && !data.className.endsWith("package-info")) {
                        classes.add(data.asMap());
                    }
                } catch (RuntimeException ex) {
                    // Ignore malformed/nonstandard entries and continue scanning the rest.
                }
            }
        }
        classes.sort((a, b) -> String.valueOf(a.get("className")).compareToIgnoreCase(String.valueOf(b.get("className"))));
        return classes;
    }

    private static final class ClassData extends ClassVisitor {
        private String className;
        private String superName;
        private int access;
        private final List<Map<String, Object>> methods = new ArrayList<>();

        private ClassData() { super(Opcodes.ASM9); }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
            this.superName = superName == null ? "" : superName.replace('/', '.');
            this.access = access;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.startsWith("<")) return null;
            Type method = Type.getMethodType(descriptor);
            List<String> params = new ArrayList<>();
            for (Type arg : method.getArgumentTypes()) params.add(arg.getClassName());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("descriptor", descriptor);
            m.put("returnType", method.getReturnType().getClassName());
            m.put("parameterTypes", params);
            m.put("public", (access & Opcodes.ACC_PUBLIC) != 0);
            m.put("static", (access & Opcodes.ACC_STATIC) != 0);
            m.put("abstract", (access & Opcodes.ACC_ABSTRACT) != 0);
            m.put("native", (access & Opcodes.ACC_NATIVE) != 0);
            m.put("eligible", (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE))
                    == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
            methods.add(m);
            return null;
        }

        Map<String, Object> asMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("className", className);
            result.put("superName", superName);
            result.put("interface", (access & Opcodes.ACC_INTERFACE) != 0);
            result.put("abstract", (access & Opcodes.ACC_ABSTRACT) != 0);
            result.put("methods", methods);
            return result;
        }
    }
}
