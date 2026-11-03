package com.rwtema.funkylocomotion.asm;

import static org.objectweb.asm.Opcodes.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;

import net.minecraft.launchwrapper.LaunchClassLoader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.base.Throwables;
import com.rwtema.funkylocomotion.LogHelper;
import com.rwtema.funkylocomotion.helper.ItemHelper;
import com.rwtema.funkylocomotion.items.ItemWrench;

public class WrenchFactory {

    private static final String GENERATED_NAME = "FLM_ItemWrench";

    public static ItemWrench makeMeAWrench() {
        ArrayList<ClassNode> nodes = new ArrayList<ClassNode>(ItemHelper.wrenchClassNames.length);
        ArrayList<String> ifaceList = new ArrayList<String>(ItemHelper.wrenchClassNames.length);
        for (String wrenchClassName : ItemHelper.wrenchClassNames) {
            ClassNode node = readClassNode(wrenchClassName);
            if (node == null || (node.access & ACC_INTERFACE) == 0) continue;

            nodes.add(node);
            ifaceList.add(wrenchClassName.replace('.', '/'));
        }

        if (nodes.isEmpty()) return new ItemWrench();

        HashSet<String> methods = new HashSet<String>();

        ClassNode wrenchNode = readClassNode(ItemWrench.class.getName());
        if (wrenchNode != null) {
            for (MethodNode method : wrenchNode.methods) {
                methods.add(getMethodDesc(method));
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        MethodVisitor mv;

        String superName = Type.getInternalName(ItemWrench.class);

        String[] ifaces = ifaceList.toArray(new String[0]);

        // V1_8 is required to inherit interface default methods; the generated class
        // skips those methods instead of replacing their default implementations.
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, GENERATED_NAME, null, superName, ifaces);

        cw.visitSource(".dynamic", null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        for (ClassNode node : nodes) {
            for (MethodNode method : node.methods) {
                if (!shouldCopyMethod(method.access, method.name)) continue;

                String mn = getMethodDesc(method);
                if (methods.contains(mn)) continue;
                methods.add(mn);

                Type returnType = Type.getReturnType(method.desc);
                int returnOpCode = returnType.getOpcode(IRETURN);

                int access = ACC_PUBLIC | (method.access & ACC_VARARGS);
                mv = cw.visitMethod(access, method.name, method.desc, null, null);
                mv.visitCode();

                switch (returnOpCode) {
                    case RETURN:
                        mv.visitInsn(RETURN);
                        break;
                    case IRETURN:
                        mv.visitInsn(returnType == Type.BOOLEAN_TYPE ? ICONST_1 : ICONST_0);
                        mv.visitInsn(IRETURN);
                        break;
                    case LRETURN:
                        mv.visitInsn(LCONST_0);
                        mv.visitInsn(LRETURN);
                        break;
                    case FRETURN:
                        mv.visitInsn(FCONST_0);
                        mv.visitInsn(FRETURN);
                        break;
                    case DRETURN:
                        mv.visitInsn(DCONST_0);
                        mv.visitInsn(DRETURN);
                        break;
                    case ARETURN:
                        mv.visitInsn(ACONST_NULL);
                        mv.visitInsn(ARETURN);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported return opcode " + returnOpCode);
                }

                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }

        cw.visitEnd();

        Class<?> ret = (new ASMClassLoader()).define(GENERATED_NAME, cw.toByteArray());

        try {
            return (ItemWrench) ret.newInstance();
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Only copy abstract public interface methods. Static, private, default,
     * synthetic/bridge and initializer methods must not become concrete stubs;
     * copying {@code <clinit>} in particular produces an illegal class file.
     */
    static boolean shouldCopyMethod(int access, String name) {
        if ("<init>".equals(name) || "<clinit>".equals(name)) return false;
        if ((access & ACC_PUBLIC) == 0) return false;
        if ((access & ACC_ABSTRACT) == 0) return false;
        return (access & (ACC_STATIC | ACC_PRIVATE | ACC_PROTECTED | ACC_NATIVE | ACC_SYNTHETIC | ACC_BRIDGE)) == 0;
    }

    private static ClassNode readClassNode(String className) {
        byte[] classBytes = getClassBytes(className);
        if (classBytes == null) return null;

        try {
            ClassNode node = new ClassNode(ASM5);
            new ClassReader(classBytes).accept(node, ClassReader.EXPAND_FRAMES);
            return node;
        } catch (Throwable t) {
            LogHelper.errorThrowable("Unable to read wrench interface " + className, t);
            return null;
        }
    }

    private static byte[] getClassBytes(String className) {
        ClassLoader launchLoader = WrenchFactory.class.getClassLoader();
        if (launchLoader instanceof LaunchClassLoader) {
            try {
                return ((LaunchClassLoader) launchLoader).getClassBytes(className);
            } catch (IOException e) {
                LogHelper.errorThrowable("Unable to read wrench interface " + className, e);
                return null;
            }
        }

        // Plain class loader (unit tests, non-Launch environments).
        String resourceName = className.replace('.', '/') + ".class";
        try (InputStream stream = WrenchFactory.class.getResourceAsStream("/" + resourceName)) {
            if (stream == null) return null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        } catch (IOException e) {
            LogHelper.errorThrowable("Unable to read wrench interface " + className, e);
            return null;
        }
    }

    private static String getMethodDesc(MethodNode method) {
        return method.name + "_" + method.desc;
    }

    private static class ASMClassLoader extends ClassLoader {

        private ASMClassLoader() {
            super(WrenchFactory.class.getClassLoader());
        }

        public Class<?> define(String name, byte[] data) {
            return defineClass(name, data, 0, data.length);
        }
    }

}
