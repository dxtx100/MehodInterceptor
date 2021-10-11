package com.dxtx.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.example.zghplugin.jg.JGClassVisitor
import com.example.zghplugin.jg.MethodInterceptorConfig
import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

import javax.annotation.Nonnull

class interceptorPlugin extends Transform implements Plugin<Project> {
    private static MethodInterceptorConfig config;

    @Override
    void apply(Project project) {
        def android = project.extensions.getByType(AppExtension)
        android.registerTransform(this)
        config = project.extensions.create("interceptor", MethodInterceptorConfig.class)
    }

    @Override
    String getName() {
        return "interceptor"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return [QualifiedContent.DefaultContentType.CLASSES]
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return [QualifiedContent.Scope.PROJECT]
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(@Nonnull TransformInvocation transformInvocation) {
        println '--------------- LifecyclePlugin visit start --------------- '
        def startTime = System.currentTimeMillis()
        Collection<TransformInput> inputs = transformInvocation.inputs
        TransformOutputProvider outputProvider = transformInvocation.outputProvider

        config.change();

        //删除之前的输出
        if (outputProvider != null)
            outputProvider.deleteAll()
        //遍历inputs
        inputs.each { TransformInput input ->
            //遍历directoryInputs
            input.directoryInputs.each { DirectoryInput directoryInput ->
                handleDirectoryInput(directoryInput, outputProvider)
            }

            //遍历jarInputs,暂不处理jar
//            input.jarInputs.each { JarInput jarInput ->
//                handleJarInputs(jarInput, outputProvider)
//            }
            // jar文件，如第三方依赖
            input.jarInputs.each { jarInput ->
                def dest = transformInvocation.outputProvider.getContentLocation(jarInput.name,
                        jarInput.contentTypes, jarInput.scopes, Format.JAR)
                FileUtils.copyFile(jarInput.file, dest)
            }
        }

        def cost = (System.currentTimeMillis() - startTime) / 1000
        println '--------------- LifecyclePlugin visit end --------------- '
        println "LifecyclePlugin cost ： $cost s"
    }

    /**
     * 处理文件目录下的class文件
     */
    static void handleDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider) {
        //是否是目录
        if (directoryInput.file.isDirectory()) {
            //列出目录所有文件（包含子文件夹，子文件夹内文件）
            directoryInput.file.eachFileRecurse { File file ->
                def name = file.name
                if (checkClassFile(file.getPath())) {
//                    println '----------- deal with "class" file <' + name + '> -----------'
                    ClassReader classReader = new ClassReader(file.bytes)
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    ClassVisitor cv = new JGClassVisitor(classReader, classWriter, config)
                    classReader.accept(cv, ClassReader.EXPAND_FRAMES)
                    byte[] code = classWriter.toByteArray()
                    FileOutputStream fos = new FileOutputStream(
                            file.parentFile.absolutePath + File.separator + name)
                    fos.write(code)
                    fos.close()
                }
            }
        }
        //处理完输入文件之后，要把输出给下一个任务
        def dest = outputProvider.getContentLocation(directoryInput.name,
                directoryInput.contentTypes, directoryInput.scopes,
                Format.DIRECTORY)
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    /**
     * 处理Jar中的class文件
     */
   /* static void handleJarInputs(JarInput jarInput, TransformOutputProvider outputProvider) {
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
            //重名名输出文件,因为可能同名,会覆盖
            def jarName = jarInput.name
            def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            File tmpFile = new File(jarInput.file.getParent() + File.separator + "classes_temp.jar")
            //避免上次的缓存被重复插入
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
            //用于保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                //插桩class
                *//*   if (checkClassFile(entryName)) {
                       //class文件处理
                       println '----------- deal with "jar" class file <' + entryName + '> -----------'
                       jarOutputStream.putNextEntry(zipEntry)
                       ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
                       ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                       ClassVisitor cv = new LifecycleClassVisitor(classWriter)

                       classReader.accept(cv, ClassReader.EXPAND_FRAMES)
                       byte[] code = classWriter.toByteArray()
                       jarOutputStream.write(code)
                   } else {
                       jarOutputStream.putNextEntry(zipEntry)
                       jarOutputStream.write(IOUtils.toByteArray(inputStream))
                   }*//*
                jarOutputStream.putNextEntry(zipEntry)
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
                jarOutputStream.closeEntry()
            }
            //结束
            jarOutputStream.close()
            jarFile.close()
            def dest = outputProvider.getContentLocation(jarName + md5Name,
                    jarInput.contentTypes, jarInput.scopes, Format.JAR)
            FileUtils.copyFile(tmpFile, dest)
            tmpFile.delete()
        }
    }*/

    /**
     * 检查class文件是否需要处理
     * @param fileName
     * @return
     */
    static boolean checkClassFile(String name) {
        if (!name.endsWith("class")) {
            return false;
        }
//        println 'check class file  --------> ' + name
        for (String pkg : config.include) {
            if(name.contains("LiveShowTabFragment")){
                println 'check class file  --------> ' + name
                println 'pkg  --------> ' + pkg
                println 'result  --------> ' + name.contains(pkg)
            }
            if (name.contains(pkg)) return true
        }
        //只处理需要的class文件
        return false
    }

}