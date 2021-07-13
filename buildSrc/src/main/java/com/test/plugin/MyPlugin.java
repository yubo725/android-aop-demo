package com.test.plugin;

import com.android.build.gradle.BaseExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class MyPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        // 在Plugin中注册自定义的Transform
        BaseExtension baseExtension = target.getExtensions().findByType(BaseExtension.class);
        if (baseExtension != null) {
            baseExtension.registerTransform(new MyTransform());
        }
    }
}
