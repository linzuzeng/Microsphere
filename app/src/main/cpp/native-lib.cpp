#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_org_lezizi_microsphere_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "\nCopyright (C) 2018 Zuzeng Lin (linzuzeng@tju.edu.cn)\n \n"
            " * This program is free software; you can redistribute it \n"
            " * and/or modify it under the terms of the GNU General  \n"
            " * Public License as published by the Free Software \n"
            " * Foundation; either version 2 of the License, or (at  \n"
            " * your option) any later version.\n\n"
            "Credits \n"
            "OpenCV\nCopyright (C) 2013, OpenCV Foundation, all rights reserved.\n"
            "Android \nCopyright (C) 2017 Google Inc. all rights reserved. \n"
            "Tensorflow\nCopyright (C) 2017 Google Inc. all rights reserved. \n"
            "MPAndroidChart\nCopyright (C) 2016 Philipp Jahoda\n"
            "Album\nCopyright 2017 Yan Zhenjie\n";
    return env->NewStringUTF(hello.c_str());
}
