package com.test.plugin.utils;

import com.example.annotation.ClickOnce;
import com.example.annotation.CountTime;

import java.io.File;
import java.util.Locale;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;

public class InjectUtil {

    private static ClassPool classPool = new ClassPool();
    // android.jar包路径
    private static String androidJarPath = "/Users/yubo/Library/Android/sdk/platforms/android-30/android.jar";
    // 项目主module下编译后的class文件路径
    private static String mainModuleClassPath = "/Users/yubo/AndroidStudioProjects/AopDemo/app/build/intermediates/javac/debug/classes";

    static {
        try {
            classPool.appendClassPath(androidJarPath);
            classPool.appendClassPath(mainModuleClassPath);
            // 导入包，否则注入代码时会提示找不到类
            classPool.importPackage("android.widget.Toast");
            classPool.importPackage("android.util.Log");
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 遍历目录，是文件则调用doInjection注入，是目录则递归调用inject方法
     * @param dirPath
     */
    public static void inject(String dirPath) {
        File f = new File(dirPath);
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    // 是目录，继续调用inject
                    inject(file.getAbsolutePath());
                } else {
                    // 是文件，注入class
                    doInjection(file.getAbsolutePath());
                }
            }
        } else {
            doInjection(dirPath);
        }
    }

    /**
     * 该方法完成对class文件的注入，功能包括：
     * 1. 在所有Activity的onCreate方法开始处插入一个Toast
     * 2. 使用@ClickOnce处理快速点击重复触发点击事件的问题
     * 3. 使用@CountTime统计某个方法的执行时间
     * @param filePath
     */
    private static void doInjection(String filePath) {
        if (filePath == null || filePath.length() == 0
                || filePath.trim().length() == 0
                || !filePath.endsWith(".class")
                || filePath.endsWith("BuildConfig.class")
                || filePath.endsWith("R.class")
                || filePath.endsWith("App.class")) {
            return;
        }
        // 功能1
        addToast(filePath);
        // 功能2
        ensureClickOnce(filePath);
        // 功能3
        calculateMethodTime(filePath);
    }

    /**
     * 使用Javassist操作Class字节码，在所有Activity的onCreate方法中插入Toast
     * @param filePath class文件路径
     */
    private static void addToast(String filePath) {
        try {
            CtClass ctClass = classPool.getCtClass(getFullClassName(filePath));
            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }
            // 获取Activity中的onCreate方法
            CtMethod onCreate = ctClass.getDeclaredMethod("onCreate");
            // 要插入的代码
            // getSimpleClassName()方法返回的是类名如"MainActivity"
            String insertCode = String.format(Locale.getDefault(),
                    "Toast.makeText(this, \"%s\", Toast.LENGTH_SHORT).show();",
                    getSimpleClassName(filePath));
            // 在onCreate方法开始处插入上面的Toast代码
            onCreate.insertBefore(insertCode);
            // 写回原来的目录下，覆盖原来的class文件
            // mainModuleClassPath是Android项目主module存放class的路径，一般是"<Android项目根目录>/app/build/intermediates/javac/debug/classes/"
            ctClass.writeFile(mainModuleClassPath);
            ctClass.detach();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理被@ClickOnce注解的方法，确保这个方法在600ms内只执行一次
     * @param filePath
     */
    private static void ensureClickOnce(String filePath) {
        try {
            CtClass ctClass = classPool.getCtClass(getFullClassName(filePath));
            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }
            CtMethod[] declaredMethods = ctClass.getDeclaredMethods();
            // 类中是否有被@ClickOnce注解的方法
            boolean clzHasClickAnnotation = false;
            for (CtMethod m : declaredMethods) {
                if (m.hasAnnotation(ClickOnce.class)) {
                    clzHasClickAnnotation = true;
                    break;
                }
            }
            // 如果类中有被@ClickOnce注解的方法，则创建新方法，并在所有被@ClickOnce注解的方法开始处插入检查代码
            if (clzHasClickAnnotation) {
                // 创建新方法并添加到类中
                createClickOnceMethod(ctClass);
                // 重新读取并加载class，因为上一步中写入了新的方法
                ctClass = classPool.get(getFullClassName(filePath));
                if (ctClass.isFrozen()) ctClass.defrost();
                declaredMethods = ctClass.getDeclaredMethods();
                for (CtMethod m : declaredMethods) {
                    if (m.hasAnnotation(ClickOnce.class)) {
                        System.out.println("found @ClickOnce method: " + m.getName());
                        // 在当前被@ClickOnce注解的方法体前面执行上面创建的新方法
                        m.insertBefore("if (!is$Click$Valid()) {" +
                                "Log.d(\"ClickOnce\", \"Click too fast, ignore this click...\");" +
                                "return;" +
                                "}");
                    }
                }
            }
            ctClass.writeFile(mainModuleClassPath);
            ctClass.detach();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 构造一个新的方法，方法体为：
     * private boolean is$Click$Valid() {
     *     long time = System.currentTimeMills();
     *     boolean canClick = (time - lastClickTime > 600L);
     *     lastClickTime = time;
     *     return canClick;
     * }
     * 如果点击有效，上面的方法返回true，否则返回false
     * @param clz
     * @throws Exception
     */
    private static void createClickOnceMethod(CtClass clz) throws Exception {
        // 先给类新增一个成员变量，用于记录上次点击的时间
        CtField lastClickField = new CtField(CtClass.longType, "lastClickTime", clz);
        lastClickField.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
        clz.addField(lastClickField);
        // 新的方法体
        String body = "{" +
                "long time = System.currentTimeMillis();" +
                "boolean canClick = (time - lastClickTime > 600);" +
                "lastClickTime = time;" +
                "return canClick;" +
                "}";
        // 创建新方法并添加到类中
        CtMethod newMethod = CtNewMethod.make(Modifier.PRIVATE, CtClass.booleanType, "is$Click$Valid",
                new CtClass[]{}, new CtClass[]{}, body, clz);
        clz.addMethod(newMethod);
        clz.writeFile(mainModuleClassPath);
    }

    /**
     * 通过注入字节码，计算被@CountTime注解的方法执行消耗的时间
     * @param filePath class文件路径
     */
    private static void calculateMethodTime(String filePath) {
        try {
            CtClass ctClass = classPool.getCtClass(getFullClassName(filePath));
            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }
            CtMethod[] declaredMethods = ctClass.getDeclaredMethods();
            for (CtMethod m : declaredMethods) {
                if (m.hasAnnotation(CountTime.class)) {
                    // 定义long类型的局部变量start
                    m.addLocalVariable("start", CtClass.longType);
                    // 在方法体开始处插入代码记录时间
                    m.insertBefore("start = System.currentTimeMillis();");
                    // 定义long类型的局部变量end
                    m.addLocalVariable("end", CtClass.longType);
                    // 在方法体结束时插入代码记录时间
                    m.insertAfter("end = System.currentTimeMillis();");
                    // 获取方法名
                    String methodName = m.getName();
                    String logMsg = "execute " + methodName + " use time: ";
                    m.insertAfter(String.format(Locale.getDefault(), "Log.d(\"%s\", \"%s\" + (end - start));", "CountTime", logMsg));
                }
            }
            ctClass.writeFile(mainModuleClassPath);
            ctClass.detach();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 修复jar包中的bug
     * @param jarFilePath jar包路径
     */
    public static byte[] fixJarBug(String jarFilePath) throws Exception {
        classPool.appendClassPath(jarFilePath);
        CtClass ctClass = classPool.getCtClass("com.bug.calculator.BugCalculator");
        if (ctClass.isFrozen()) {
            ctClass.defrost();
        }
        // 创建一个新的方法fAdd，接收两个float参数，返回一个float值
        CtMethod newMethod = CtNewMethod.make("public static float fAdd(float a, float b) { return a + b; }", ctClass);
        ctClass.addMethod(newMethod);
        byte[] bytes = ctClass.toBytecode();
        ctClass.detach();
        return bytes;
    }

    /**
     * 获取类名，如：MainActivity
     * @param filePath
     * @return
     */
    private static String getSimpleClassName(String filePath) {
        return filePath.substring(filePath.lastIndexOf("/") + 1).replace(".class", "");
    }

    /**
     * 获取全类型，如：com.example.aopdemo.MainActivity
     * @param filePath
     * @return
     */
    private static String getFullClassName(String filePath) {
        return "com.example.aopdemo." + getSimpleClassName(filePath);
    }
}
