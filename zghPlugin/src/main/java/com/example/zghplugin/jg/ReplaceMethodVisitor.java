package com.example.zghplugin.jg;

import com.google.gson.JsonObject;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;


import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;


/**
 * @author gavin
 * @date 2019/2/19
 */
public class ReplaceMethodVisitor extends MethodVisitor {
    private MethodInterceptorConfig config;
    public int access;
    public String desc, signature, name;
    public String[] exceptions;
    ClassVisitor cv;
    public String className;
    public Type classType;

    public MethodVisitor originMV;
    public String replaceMethodName;
    public MethodVisitor replaceMethodVisitor;
    private Type[] params;
    int paramsCount = 0;

    public ReplaceMethodVisitor(int api, MethodVisitor mv, MethodInterceptorConfig config) {
        super(api, mv);
        this.config = config;
        //保存旧方法Visitor,以便在旧方法中写一些新的代码
        originMV = mv;
    }

    public void setDesc(String desc) {
        this.desc = desc;
        params = Type.getArgumentTypes(desc);
        if (params == null) params = new Type[0];
        paramsCount = params.length;
        Log.d("params count = " + paramsCount);
    }

    JGAnnotationVisitor annotationVisitor;

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
//        if (annotationVisitor != null) {
//            //注解可能有多个~其中已有一个注解被解析了~现在解析第二个注解比如@Overried,这个无关紧要的~.
//            return annotationVisitor;
//        }
        if (config.handlers.containsKey(desc)) {
            Log.ddd("annotation " + desc);
            Log.ddd("method : " + className + " " + this.desc);
            annotationVisitor = new JGAnnotationVisitor(super.visitAnnotation(desc, visible), config.handlers.get(desc));

            //属性mv使用替换方法;在读取方法时,mv会写到新方法内..新方法是基本copy旧方法的
            mv = replaceMethodVisitor = cv.visitMethod(getReplaceAccess(), getReplaceName(desc), this.desc, signature, exceptions);

            return annotationVisitor;
        } else {
//            mv = originMV;
            return super.visitAnnotation(desc, visible);
        }
    }

    /**
     * 返回替换方法名字
     * @param desc
     * @return
     */
    private String getReplaceName(String desc) {
        String annotation_name = desc.substring(desc.lastIndexOf("/") + 1, desc.length() - 1).toLowerCase();
        replaceMethodName = replaceMethodName.replace("$annotation_name$", annotation_name);
        return replaceMethodName;
    }

    /**
     * 返回替换方法的修饰符
     * @return
     */
    private int getReplaceAccess() {
        int _access = access & 0xfff9;//->二进制1111 1111 1111 1001,移除两个修饰符ACC_PRIVATE,ACC_PROTECTED..
        _access |= ACC_PUBLIC;// "|="一般表示addFlag,这里修改方法为public,保留final,static,native等等修饰符
        return _access;
    }


    @Override
    public void visitCode() {
        super.visitCode();
        if (replaceMethodVisitor != null) {
            visitOriginCode(originMV);
        }
    }


    private void insertNumber(MethodVisitor methodVisitor, int number) {
        int[] code = new int[]{ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5};
        if (number <= 5) {
            //小于5使用这种命令
            methodVisitor.visitInsn(code[number]);
        } else {
            //大于5使用如下命令
            methodVisitor.visitIntInsn(BIPUSH, number);
        }

    }

    private int insertArgs(MethodVisitor mv) {
        //非静态方法,栈底0为this obj,需要多加一个index
        //而静态方法,直接从0开始
        int index = isStatic() ? 0 : 1;

        for (int i = 0; i < paramsCount; i++) {
            mv.visitInsn(DUP);
            index = insertArg(mv, params[i], i, index);
//            insertNumber(mv, i);
//            mv.visitVarInsn(ALOAD, i + 1);
            mv.visitInsn(AASTORE);
        }

        return index;
    }

    private int insertArg(MethodVisitor mv, Type type, int i, int index) {
        insertNumber(mv, i);
        if (type.getSort() > 0 && type.getSort() < 9) {//1-8基础类型
            String boxedName = getBoxedType(type);
            String methodSign = String.format("(%s)L%s;", type.getDescriptor(), boxedName);
            Log.d("boxedName--->" + boxedName + ",methodSign=" + methodSign);

            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    mv.visitVarInsn(ILOAD, index);
                    mv.visitMethodInsn(INVOKESTATIC, boxedName, "valueOf", methodSign, false);
                    return index + 1;//int占字节宽度1
                case Type.FLOAT:
                    mv.visitVarInsn(FLOAD, index);
                    mv.visitMethodInsn(INVOKESTATIC, boxedName, "valueOf", methodSign, false);
                    return index + 2;//FLOAT占字节宽度2
                case Type.LONG:
                    mv.visitVarInsn(LLOAD, index);
                    mv.visitMethodInsn(INVOKESTATIC, boxedName, "valueOf", methodSign, false);
                    return index + 2;//LONG占字节宽度2
                case Type.DOUBLE:
                    mv.visitVarInsn(DLOAD, index);
                    mv.visitMethodInsn(INVOKESTATIC, boxedName, "valueOf", methodSign, false);//DOUBLE占字节宽度2
                    return index + 2;
                default:
                    mv.visitVarInsn(ALOAD, index);//ALOAD为指针,占字节宽度1
                    return index + 1;
            }

        } else {
            mv.visitVarInsn(ALOAD, index);//ALOAD为指针,占字节宽度1
            return index + 1;
        }
    }

    private static String getBoxedType(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                return Boolean.class.getName().replace(".", "/");
            case Type.CHAR:
                return Character.class.getName().replace(".", "/");
            case Type.BYTE:
                return Byte.class.getName().replace(".", "/");
            case Type.SHORT:
                return Short.class.getName().replace(".", "/");
            case Type.INT:
                return Integer.class.getName().replace(".", "/");
            case Type.FLOAT:
                return Float.class.getName().replace(".", "/");
            case Type.LONG:
                return Long.class.getName().replace(".", "/");
            case Type.DOUBLE:
                return Double.class.getName().replace(".", "/");
            default:
                return "";
        }
    }

    private static class JGAnnotationVisitor extends AnnotationVisitor {

        private String utilsName;

        JsonObject jsonObject = new JsonObject();

        public JGAnnotationVisitor(AnnotationVisitor av, String utilsName) {
            super(ASM7, av);
            this.utilsName = utilsName;
        }


        @Override
        public void visit(String name, Object value) {
            super.visit(name, value);
            Log.d("visit name value =" + name + ":" + value);
            jsonObject.addProperty(name, value.toString());
        }

        public void visitEnum(String name, String desc, String value) {
            super.visitEnum(name, desc, value);
            Log.d("visit Enum value =" + name + ":" + value);
            jsonObject.addProperty(name, value);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return super.visitArray(name);
        }

        public String getInfo() {
            String info = jsonObject.toString();
            Log.d("visit info =" + info);
            return info;
        }
    }


  /*  public void visitOriginCode(MethodVisitor mv) {
        final int i = methodArgNumber;
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        //换行20
        insertNumber(mv, i);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        insertArg(mv, methodArgNumber);

        mv.visitVarInsn(ASTORE, i + 1);
        Label l1 = new Label();
        mv.visitLabel(l1);
        //换行21
        mv.visitLdcInsn(replaceMethodName);
        mv.visitVarInsn(ASTORE, i + 2);
        Label l2 = new Label();
        mv.visitLabel(l2);
        //换行22
        mv.visitLdcInsn(annotationVisitor.getInfo());
        mv.visitVarInsn(ASTORE, i + 3);
        Label l3 = new Label();
        mv.visitLabel(l3);
        //换行23
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;", false);
        mv.visitVarInsn(ASTORE, i + 4);
        Label l4 = new Label();
        mv.visitLabel(l4);
        //换行24
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, i + 5);
        Label l5 = new Label();
        mv.visitLabel(l5);
//        mv.visitFrame(Opcodes.F_FULL, i + 6, new Object[]{"com/company/Test", "java/lang/Object", "java/lang/Object", "java/lang/Object", "java/lang/Object", "java/lang/Object", "java/lang/Object", "java/lang/Object", "[Ljava/lang/Object;", "java/lang/String", "java/lang/String", "[Ljava/lang/reflect/Method;", Opcodes.INTEGER}, 0, new Object[]{});
        mv.visitVarInsn(ILOAD, i + 5);
        mv.visitVarInsn(ALOAD, i + 4);
        mv.visitInsn(ARRAYLENGTH);
        Label l6 = new Label();
        mv.visitJumpInsn(IF_ICMPGE, l6);
        Label l7 = new Label();
        mv.visitLabel(l7);
        //换行25
        mv.visitVarInsn(ALOAD, i + 4);
        mv.visitVarInsn(ILOAD, i + 5);
        mv.visitInsn(AALOAD);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
        mv.visitVarInsn(ALOAD, i + 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label l8 = new Label();
        mv.visitJumpInsn(IFEQ, l8);
        Label l9 = new Label();
        mv.visitLabel(l9);
        //换行26
        mv.visitVarInsn(ALOAD, i + 3);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, i + 4);
        mv.visitVarInsn(ILOAD, i + 5);
        mv.visitInsn(AALOAD);
        mv.visitVarInsn(ALOAD, i + 1);
        mv.visitMethodInsn(INVOKESTATIC, annotationVisitor.utilsName, "onMethodIntercepted", "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)V", false);
        Label l10 = new Label();
        mv.visitLabel(l10);
        //换行27
        mv.visitInsn(RETURN);
        mv.visitLabel(l8);
        //换行24
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitIincInsn(i + 5, 1);
        mv.visitJumpInsn(GOTO, l5);
        mv.visitLabel(l6);
        //换行30
        mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        mv.visitInsn(RETURN);
        Label l11 = new Label();
        mv.visitLabel(l11);
        mv.visitMaxs(4, i + 6);
        mv.visitEnd();
    }*/

    List<String> paramsNames = new ArrayList<>();

    @Override
    public void visitParameter(String name, int access) {
        super.visitParameter(name, access);
        paramsNames.add(name);
    }


    /*
        编译方法简化为
     public void testB(String e, String[] f, int[] g) {
        Object[] args = new Object[]{e, f, g};
        Chain chain = new Chain(this, "_confirmed_index3_testB", args);
        PermissionProxy.onMethodIntercepted("{\"value\":\"NeedLogin\"}", chain);
    }

     */

    /**
     * @param mv
     */

    public void visitOriginCode(MethodVisitor mv) {
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        //换行22 新建数组,个数=参数.length
        insertNumber(mv, params.length);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        //插入所有参数
        int index = insertArgs(mv);
        //数组赋值为参数列表
        mv.visitVarInsn(ASTORE, index);

        Label l1 = new Label();
        mv.visitLabel(l1);
        //换行23
        mv.visitTypeInsn(NEW, "com/dxtx/interceptor/Chain");
        //load 对象this
        mv.visitInsn(DUP);

        //加载Chain构造函数的参数
        if (isStatic()) {
//            mv.visitInsn(ACONST_NULL);
            mv.visitLdcInsn(classType);//参数1,静态方法,需要class描述,方法所在的class
        } else {
            mv.visitVarInsn(ALOAD, 0);//参数1,this
        }
        mv.visitLdcInsn(replaceMethodName);//参数2 方法名
        mv.visitVarInsn(ALOAD, index);//参数3 Object[]
        mv.visitMethodInsn(INVOKESPECIAL, "com/dxtx/interceptor/Chain", "<init>", "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)V", false);
        mv.visitVarInsn(ASTORE, index + 1);
        Label l2 = new Label();
        mv.visitLabel(l2);
        //换行24
        mv.visitLdcInsn(annotationVisitor.getInfo());
        mv.visitVarInsn(ALOAD, index + 1);
        mv.visitMethodInsn(INVOKESTATIC, annotationVisitor.utilsName, "onMethodIntercepted", "(Ljava/lang/String;Lcom/dxtx/interceptor/Chain;)V", false);
        Label l3 = new Label();
        mv.visitLabel(l3);
        //换行25
        mv.visitInsn(RETURN);
        Label l4 = new Label();
        mv.visitLabel(l4);
//        mv.visitLocalVariable("this", "Lcom/company/Test;", null, l0, l4, 0);
//        for (int j = 0; j < length; j++) {
//            mv.visitLocalVariable(paramsNames.get(j), params.get(j), null, l0, l4, j + 1);
//        }
//        mv.visitLocalVariable("chain", "Lcom/dxtx/interceptor/Chain;", null, l2, l4, length + 2);
        mv.visitMaxs(5, index + 2);
        mv.visitEnd();
    }

    private boolean isStatic() {
        Log.d("access=0x" + Integer.toHexString(access) + ",access & 8 -->0x" + Integer.toHexString(access & 8));
        return ACC_STATIC == (access & 8);
    }

}
