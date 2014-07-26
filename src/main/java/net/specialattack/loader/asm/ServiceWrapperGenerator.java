package net.specialattack.loader.asm;


import net.specialattack.loader.BootClassLoader;
import net.specialattack.loader.IServiceWrapper;
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

public class ServiceWrapperGenerator implements Opcodes {

    @SuppressWarnings("unchecked")
    public static Class<? extends IServiceWrapper> generateClass(Type type, BootClassLoader loader) {
        Type wrapperType = Type.getObjectType(type.getInternalName() + "$wrapper");

        ClassWriter writer = new ClassWriter(0);
        CheckClassAdapter cw = new CheckClassAdapter(writer);

        MethodVisitor mv;

        cw.visit(V1_7, ACC_PUBLIC | ACC_SUPER, wrapperType.getInternalName(), null, "java/lang/Object", new String[] { "net/specialattack/loader/IServiceWrapper" });

        cw.visitSource(".dynamic", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("this", wrapperType.getDescriptor(), null, l0, l1, 0);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "start", "()V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitMethodInsn(INVOKESTATIC, type.getInternalName(), "startService", "()V", false);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitInsn(RETURN);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("this", wrapperType.getDescriptor(), null, l0, l2, 0);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "stop", "()V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitMethodInsn(INVOKESTATIC, type.getInternalName(), "stopService", "()V", false);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitInsn(RETURN);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("this", wrapperType.getDescriptor(), null, l0, l2, 0);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "getBaseClass", "()Ljava/lang/Class;", "()Ljava/lang/Class<*>;", null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLdcInsn(type);
            mv.visitInsn(ARETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("this", wrapperType.getDescriptor(), null, l0, l1, 0);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        return (Class<? extends IServiceWrapper>) loader.define(wrapperType.getClassName(), writer.toByteArray());
    }

}
