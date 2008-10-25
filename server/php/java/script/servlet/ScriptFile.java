
/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import php.java.servlet.CGIServlet;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
final class ScriptFile extends File {
    private static final long serialVersionUID = 5388841733118702557L;
    private String webPath;
    
    private String getWebPath(String webDir, String baseName, String fileName) {
        String path1 = baseName;
        path1=path1.replace('\\', '/');
        if(!path1.endsWith("/")) path1+="/";
        String path2 = fileName;
        path2=path2.replace('\\', '/');
        String path3 = path2.substring(path1.length());
        if (!webDir.endsWith("/")) webDir+="/";
        return webDir+path3;
    }
    public ScriptFile(String fileName) {
        super(fileName);
    }
    
    public String getWebPath(String fileName, HttpServletRequest req, ServletContext servletCtx) throws IOException {
        if (webPath!=null) return webPath;
        return webPath=getWebPath(req.getContextPath(), new File(CGIServlet.getRealPath(servletCtx, "")).getCanonicalPath(), fileName);
    }
}