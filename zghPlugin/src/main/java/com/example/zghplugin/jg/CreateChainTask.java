package com.example.zghplugin.jg;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 描述：生成Chain.java
 * <p>
 * author: Tiger
 * date: 2021/10/12
 * version 1.0
 */
public class CreateChainTask extends DefaultTask {

    @TaskAction
    void doTask() {

        File dir = new File(getProject().getBuildDir(), "generated/intercept_chain_source_out");

        Log.d("dir->" + dir.getAbsolutePath());

        dir.mkdirs();

        File javaFile = new File(dir, "com/dxtx/interceptor/Chain.java");

        if (!javaFile.getParentFile().exists()) {
            javaFile.getParentFile().mkdirs();
        }

        if (!javaFile.exists()) {
            try {
                javaFile.createNewFile();
                FileWriter writer = new FileWriter(javaFile);
                writer.write(getText());

                writer.flush();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private String getText() {
        return "package com.dxtx.interceptor;\n" +
                "\n" +
                "import java.lang.reflect.InvocationTargetException;\n" +
                "import java.lang.reflect.Method;\n" +
                "public class Chain {\n" +
                "    private Object caller;\n" +
                "    private String methodName;\n" +
                "    private Object[] args;\n" +
                "\n" +
                "    public Chain(Object caller, String methodName, Object[] args) {\n" +
                "        this.caller = caller;\n" +
                "        this.methodName = methodName;\n" +
                "        this.args = args;\n" +
                "    }\n" +
                "\n" +
                "    public void process() {\n" +
                "        Class<?> clz;\n" +
                "        if (caller instanceof Class) {\n" +
                "            clz = (Class<?>) caller;\n" +
                "        } else {\n" +
                "            clz = caller.getClass();\n" +
                "        }\n" +
                "        Method[] ms = clz.getDeclaredMethods();\n" +
                "        for (Method m : ms) {\n" +
                "            if (m.getName().equals(methodName)) {\n" +
                "                try {\n" +
                "                    if (args == null || args.length == 0) {\n" +
                "                        m.invoke(caller);\n" +
                "                    } else {\n" +
                "                        m.invoke(caller, args);\n" +
                "                    }\n" +
                "                } catch (IllegalAccessException | InvocationTargetException e) {\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "                return;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";
    }

}
