package net.specialattack.loader;

import net.specialattack.loader.asm.ServiceWrapperGenerator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServiceData {

    public final BootClassLoader loader;
    public final Set<String> services;
    public final Set<String> trackableClasses;
    public final Set<IServiceWrapper> serviceWrappers;

    public ServiceData(BootClassLoader loader) {
        this.loader = loader;
        this.services = new HashSet<String>();
        this.trackableClasses = new HashSet<String>();
        this.loader.addClassTransformer(new ClassTransformer());
        this.serviceWrappers = new HashSet<IServiceWrapper>();
    }

    private class ClassTransformer implements IClassTransformer {

        @Override
        @SuppressWarnings("unchecked")
        public byte[] transform(String name, byte[] original) {
            if (!ServiceData.this.services.contains(name)) {
                return original;
            }
            ClassReader reader = new ClassReader(original);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            List<MethodNode> methods = (List<MethodNode>) node.methods;
            boolean changed = false;
            boolean hasStart = false;
            boolean hasStop = false;

            for (MethodNode method : methods) {
                if ((method.access & Opcodes.ACC_STATIC) != 0) {
                    if (method.name.equals("startService")) {
                        int prev = method.access;
                        method.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC);
                        method.access |= Opcodes.ACC_PUBLIC;
                        if (prev != method.access) {
                            changed = true;
                        }
                        hasStart = true;
                    }
                    if (method.name.equals("stopService")) {
                        int prev = method.access;
                        method.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC);
                        method.access |= Opcodes.ACC_PUBLIC;
                        if (prev != method.access) {
                            changed = true;
                        }
                        hasStop = true;
                    }
                }
            }

            if (hasStart && hasStop) {
                Type type = Type.getObjectType(name.replaceAll("\\.", "/"));
                Class<? extends IServiceWrapper> clazz = ServiceWrapperGenerator.generateClass(type, ServiceData.this.loader);
                try {
                    ServiceData.this.serviceWrappers.add(clazz.newInstance());
                } catch (Throwable e) {
                    throw new RuntimeException("Failed creating service wrapper class " + clazz.getName(), e);
                }
            } else if (!hasStart && hasStop) {
                throw new RuntimeException("Service class has a 'stopService' method, but not a 'startService' method");
            } else if (hasStart && !hasStop) {
                throw new RuntimeException("Service class has a 'startService' method, but not a 'stopService' method");
            }

            if (changed) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            } else {
                return original;
            }
        }
    }

}
