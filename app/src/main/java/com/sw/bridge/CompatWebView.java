package com.sw.bridge;


import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebView;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class CompatWebView extends WebView {

    private boolean isInjectCompatJsFlag = false;
    private static final String DEFAULT_SCHEME = "CompatScheme";
    private static final String JAVASCRIPT_ANNOTATION = "@android.webkit.JavascriptInterface()";
    private String scheme;
    private HashMap<String, Object> injectHashMap = new HashMap<>();

    public CompatWebView(Context context) {
        this(context, null);
    }

    public CompatWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        scheme = DEFAULT_SCHEME.toLowerCase();
        setWebViewClient(new CompatWebViewClient());
    }

    public void setScheme(String scheme) {
        if (TextUtils.isEmpty(scheme)) {
            return;
        }
        this.scheme = scheme.toLowerCase();
    }

    public void compatEvaluateJavascript(String javascript) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            loadUrl("javascript:" + javascript);
        } else {
            evaluateJavascript("javascript:" + javascript, null);
        }
    }

    @SuppressLint({"JavascriptInterface", "AddJavascriptInterface"})
    public void compatAddJavascriptInterface(Object object, String name) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
//            super.addJavascriptInterface(object, name);
//        } else {
//
//        }
        injectHashMap.put(name, object);
        initCompatJs();
        injectJsInterfaceForCompat(object, name);

    }

    private void initCompatJs() {
        if (!isInjectCompatJsFlag) {
            isInjectCompatJsFlag = true;
            String initIFrame = "compatMsgIFrame = document.createElement('iframe');\n" +
                    "            compatMsgIFrame.style.display = 'none';\n" +
                    "            document.documentElement.appendChild(compatMsgIFrame);";
            compatEvaluateJavascript(initIFrame);
        }
    }


    private void injectJsInterfaceForCompat(Object object, String name) {
        Class clazz = object.getClass();
        Method[] methods = clazz.getMethods();
        if (methods == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("window.JInterface = {");
        for (Method method : methods) {
            if (!checkMethodValid(method)) {
                return;
            }
            sb.append(method.getName()).append("(");
            Class<?>[] parameterTypes = method.getParameterTypes();
            int paramSize = parameterTypes.length;
            List<String> paramList = new ArrayList<>();
            for (int i = 0; i < paramSize; i++) {
                String tmp = "param" + i;
                sb.append(tmp);
                paramList.add(tmp);
                if (i < (paramSize - 1)) {
                    sb.append(",");
                }
            }
            sb.append("){schemeEncode = encodeURIComponent(\"").append(name).append("?fun=").append(method.getName());
            if (paramList.size() == 0) {
                sb.append("\"");
            } else {
                for (int i = 0; i < paramList.size(); i++) {
                    sb.append("&").append(paramList.get(i)).append("=\"+").append(paramList.get(i));
                    if (i < (paramSize - 1)) {
                        sb.append("+\"");
                    }
                }
            }

            sb.append("); compatMsgIFrame.src =\"").append(scheme).append("://\"").append("+schemeEncode;}");
        }
        sb.append("}");
        compatEvaluateJavascript(sb.toString());
    }

    private static boolean checkMethodValid(Method method){
        Annotation[] annotations = method.getAnnotations();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (JAVASCRIPT_ANNOTATION.equals(annotation.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        try {
            String urlDecode = URLDecoder.decode(url, "UTF-8");
            if (urlDecode.startsWith(scheme)) {
                Log.i("WEB_", urlDecode);
                JavaMethod javaMethod = decodeMethodFromUrl(urlDecode);
                if (javaMethod == null) {
                    return false;
                }
                return javaMethod.invoke(injectHashMap);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return false;
    }

    private JavaMethod decodeMethodFromUrl(String url) {
        if (!url.contains("?")) {
            return null;
        }

        JavaMethod javaMethod = new JavaMethod();
        int start = url.indexOf("://");
        int end = url.indexOf("?");
        if (start <= 0 || end < start) {
            return null;
        }
        javaMethod.object = url.substring(start + 3, end);
        String tmp = url.substring(end + 1, url.length());
        if (TextUtils.isEmpty(tmp)) {
            return null;
        }
        String[] urlArray = tmp.split("&");
        if (urlArray.length < 1) {
            return null;
        }
        for (int i = 0; i < urlArray.length; i++) {
            String[] params = urlArray[i].split("=");
            if (params.length != 2) {
                return null;
            }
            if (i == 0) {
                javaMethod.methodName = params[1];
            } else {
                javaMethod.params.put(params[0], params[1]);
            }
        }
        Log.i("WEB_", "m:" + javaMethod.toString());
        return javaMethod;
    }

    private static class JavaMethod {
        @Override
        public String toString() {
            return "JavaMethod{" +
                    "object='" + object + '\'' +
                    ", methodName='" + methodName + '\'' +
                    ", params=" + params +
                    '}';
        }

        String object;
        String methodName;
        LinkedHashMap<String, String> params = new LinkedHashMap<>();

        private Class<?> getParamType(String obj) {
            if (TextUtils.isDigitsOnly(obj)) {
                if (obj.contains(".")) {
                    return Float.class;
                } else {
                    return int.class;
                }
            } else {
                return String.class;
            }
        }

        boolean invoke(HashMap<String, Object> injectHashMap) {
            Object injectInstance = injectHashMap.get(object);
            if (injectInstance == null) {
                return false;
            }
            Class<?> clazz = injectInstance.getClass();
            int size = params.size();

            try {
                clazz.getMethods();



                Method method = clazz.getMethod(methodName, String.class, String.class);
                if (!checkMethodValid(method)) {
                    return false;
                }
                method.setAccessible(true);
                method.invoke(injectInstance, "ssfsf", 10);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            return true;
        }

    }
}
