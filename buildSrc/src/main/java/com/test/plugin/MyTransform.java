package com.test.plugin;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.test.plugin.utils.InjectUtil;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class MyTransform extends Transform {

    @Override
    public String getName() {
        return "MyCustomTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);
        if (!transformInvocation.isIncremental()) {
            // 非增量编译，则删除之前的所有输出
            transformInvocation.getOutputProvider().deleteAll();
        }
        // 拿到所有输入
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        if (!inputs.isEmpty()) {
            for (TransformInput input : inputs) {
                // directoryInputs保存的是存放class文件的所有目录，可以通过方法内打印的log查看具体的目录
                Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
                handleDirInputs(transformInvocation, directoryInputs);

                // jarInputs保存的是所有依赖的jar包的地址，可以通过方法内打印的log查看具体的jar包路径
                Collection<JarInput> jarInputs = input.getJarInputs();
                handleJarInputs(transformInvocation, jarInputs);
            }
        }
    }

    // 处理输入的目录
    private void handleDirInputs(TransformInvocation transformInvocation, Collection<DirectoryInput> directoryInputs) {
        for (DirectoryInput directoryInput : directoryInputs) {
            String absolutePath = directoryInput.getFile().getAbsolutePath();
//            System.out.println(">>>> directory input file path: " + absolutePath);
            // 处理class文件
            InjectUtil.inject(absolutePath);
            // 获取目标地址
            File contentLocation = transformInvocation.getOutputProvider().getContentLocation(directoryInput.getName(),
                    directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);
            // 拷贝目录
            try {
                FileUtils.copyDirectory(directoryInput.getFile(), contentLocation);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 处理输入的Jar包
    private void handleJarInputs(TransformInvocation transformInvocation, Collection<JarInput> jarInputs) {
        for (JarInput jarInput : jarInputs) {
            String absolutePath = jarInput.getFile().getAbsolutePath();
//            System.out.println(">>>> jar input file path: " + absolutePath);
            File contentLocation = transformInvocation.getOutputProvider().getContentLocation(jarInput.getName(),
                    jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
            try {
                // 匹配要修复的jar包
                if (absolutePath.endsWith("calculator-0.0.1.jar")) {
                    // 原始的jar包
                    JarFile jarFile = new JarFile(absolutePath);
                    // 处理后的jar包路径
                    String tmpJarFilePath = jarInput.getFile().getParent() + File.separator + jarInput.getFile().getName() + "_tmp.jar";
                    File tmpJarFile = new File(tmpJarFilePath);
                    JarOutputStream jos = new JarOutputStream(new FileOutputStream(tmpJarFile));
//                    System.out.println("origin jar file path: " + jarInput.getFile().getAbsolutePath());
//                    System.out.println("tmp jar file path: " + tmpJarFilePath);
                    Enumeration<JarEntry> entries = jarFile.entries();
                    // 遍历jar包中的文件，找到需要修改的class文件
                    while (entries.hasMoreElements()) {
                        JarEntry jarEntry = entries.nextElement();
                        String name = jarEntry.getName();
                        jos.putNextEntry(new ZipEntry(name));
                        InputStream is = jarFile.getInputStream(jarEntry);
                        // 匹配到有问题的class文件
                        if ("com/bug/calculator/BugCalculator.class".equals(name)) {
                            // 处理有问题的class文件并将新的数据写入到新jar包中
                            jos.write(InjectUtil.fixJarBug(absolutePath));
                        } else {
                            // 没有问题的直接写入到新的jar包中
                            jos.write(IOUtils.toByteArray(is));
                        }
                        jos.closeEntry();
                    }
                    // 关闭IO流
                    jos.close();
                    jarFile.close();
                    // 拷贝新的Jar文件
//                    System.out.println(">>>>>>>>copy to dest: " + contentLocation.getAbsolutePath());
                    FileUtils.copyFile(tmpJarFile, contentLocation);
                    // 删除临时文件
//                    System.out.println(">>>>>>>>tmpJarFile: " + tmpJarFile.getAbsolutePath());
                    tmpJarFile.delete();
                } else {
                    FileUtils.copyFile(jarInput.getFile(), contentLocation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
