package com.example.zghplugin.jg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MethodInterceptorConfig {
    private boolean doChange = false;
    public Set<String> include;

    public Map<String, String> handlers;
    public boolean anotationInClass = false;

    /**
     * 做一些转换工作
     */
    public void change() {
        if (doChange) {
            return;
        }
        if (include == null) {
            include = new HashSet<>();
        }
        if (handlers == null) {
            handlers = new HashMap<>();
        }
        Log.ddd("anotationInClass:" + anotationInClass);
        Set<String> processedInclude = new HashSet<>();
        for (String packName : include) {
            String processedName = packName.replace(".", "\\");
            Log.ddd("packName:" + processedName);
            processedInclude.add(processedName);
        }
        Map<String, String> pMap = new HashMap<>();
        for (String AnnotationName : handlers.keySet()) {
            String processedName = "L" + AnnotationName.replace(".", "/") + ";";
            String handlerName = handlers.get(AnnotationName).replace(".", "/");
            Log.ddd(processedName + ":" + handlerName);
            Log.ddd("\n");
            pMap.put(processedName, handlerName);
        }
        this.include = processedInclude;
        this.handlers = pMap;
        doChange = true;
    }
}