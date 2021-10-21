package com.example.zghplugin.jg;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;


/**
 * @author gavin
 * @date 2019/2/18
 * lifecycle class visitor
 */
public class JGClassVisitor extends ClassVisitor implements Opcodes {

    MethodInterceptorConfig config;
    private boolean needHandle = true;
    int methodIndex = 0;
    private String className;
    private Type classType;

    public JGClassVisitor(ClassReader cr, ClassWriter cv, MethodInterceptorConfig config) {
        super(Opcodes.ASM7, cv);
        this.config = config;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        className = name;
        classType = Type.getType("L" + name + ";");
        Log.d("className------------>" + className);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        Log.d("------------>" + desc);
        if(config.anotationInClass){
            needHandle = config.handlers.containsKey(desc);
        }else {
            needHandle = true;
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (needHandle) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            ReplaceMethodVisitor visitor = new ReplaceMethodVisitor(api, mv, config);
            visitor.access = access;
            visitor.name = name;
            visitor.signature = signature;
            visitor.exceptions = exceptions;
            visitor.cv  = cv;
            visitor.className  = className;
            visitor.classType = classType;
            //创建修改原有方法的名字
            visitor.replaceMethodName = "_$annotation_name$_index" + ++methodIndex + "_" + name;
            visitor.setDesc(desc);

            return visitor;
        } else {
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }

}
