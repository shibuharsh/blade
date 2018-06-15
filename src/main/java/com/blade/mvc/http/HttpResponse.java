package com.blade.mvc.http;

import com.blade.exception.BladeException;
import com.blade.exception.NotFoundException;
import com.blade.kit.StringKit;
import com.blade.mvc.ui.ModelAndView;
import com.blade.mvc.wrapper.OutputStreamWrapper;
import com.blade.server.netty.HttpConst;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * HttpResponse
 *
 * @author biezhi
 * 2017/5/31
 */
@Slf4j
public class HttpResponse implements Response {

    private HttpHeaders headers     = new DefaultHttpHeaders(false);
    private Set<Cookie> cookies     = new HashSet<>(4);
    private int         statusCode  = 200;
    private String      contentType = null;
    private Body        body;

    @Override
    public int statusCode() {
        return this.statusCode;
    }

    @Override
    public Response status(int status) {
        this.statusCode = status;
        return this;
    }

    @Override
    public Response contentType(@NonNull String contentType) {
        this.contentType = contentType;
        return this;
    }

    @Override
    public String contentType() {
        return null == this.contentType ? null : String.valueOf(this.contentType);
    }

    @Override
    public Map<String, String> headers() {
        Map<String, String> map = new HashMap<>(this.headers.size());
        this.headers.forEach(header -> map.put(header.getKey(), header.getValue()));
        if (StringKit.isNotEmpty(this.contentType)) {
            map.put(HttpConst.CONTENT_TYPE_STRING, this.contentType);
        }
        return map;
    }

    @Override
    public Response header(CharSequence name, CharSequence value) {
        this.headers.set(name, value);
        return this;
    }

    @Override
    public Response cookie(@NonNull com.blade.mvc.http.Cookie cookie) {
        Cookie nettyCookie = new io.netty.handler.codec.http.cookie.DefaultCookie(cookie.name(), cookie.value());
        if (cookie.domain() != null) {
            nettyCookie.setDomain(cookie.domain());
        }
        if (cookie.maxAge() > 0) {
            nettyCookie.setMaxAge(cookie.maxAge());
        }
        nettyCookie.setPath(cookie.path());
        nettyCookie.setHttpOnly(cookie.httpOnly());
        nettyCookie.setSecure(cookie.secure());
        this.cookies.add(nettyCookie);
        return this;
    }

    @Override
    public Response cookie(String name, String value) {
        this.cookies.add(new io.netty.handler.codec.http.cookie.DefaultCookie(name, value));
        return this;
    }

    @Override
    public Response cookie(@NonNull String name, @NonNull String value, int maxAge) {
        Cookie nettyCookie = new io.netty.handler.codec.http.cookie.DefaultCookie(name, value);
        nettyCookie.setPath("/");
        nettyCookie.setMaxAge(maxAge);
        this.cookies.add(nettyCookie);
        return this;
    }

    @Override
    public Response cookie(@NonNull String name, @NonNull String value, int maxAge, boolean secured) {
        Cookie nettyCookie = new io.netty.handler.codec.http.cookie.DefaultCookie(name, value);
        nettyCookie.setPath("/");
        nettyCookie.setMaxAge(maxAge);
        nettyCookie.setSecure(secured);
        this.cookies.add(nettyCookie);
        return this;
    }

    @Override
    public Response cookie(@NonNull String path, @NonNull String name, @NonNull String value, int maxAge, boolean secured) {
        Cookie nettyCookie = new io.netty.handler.codec.http.cookie.DefaultCookie(name, value);
        nettyCookie.setMaxAge(maxAge);
        nettyCookie.setSecure(secured);
        nettyCookie.setPath(path);
        this.cookies.add(nettyCookie);
        return this;
    }

    @Override
    public Response removeCookie(@NonNull String name) {
        Optional<Cookie> cookieOpt = this.cookies.stream().filter(cookie -> cookie.name().equals(name)).findFirst();
        cookieOpt.ifPresent(cookie -> {
            cookie.setValue("");
            cookie.setMaxAge(-1);
        });
        Cookie nettyCookie = new io.netty.handler.codec.http.cookie.DefaultCookie(name, "");
        nettyCookie.setMaxAge(-1);
        this.cookies.add(nettyCookie);
        return this;
    }

    @Override
    public Map<String, String> cookies() {
        Map<String, String> map = new HashMap<>(8);
        this.cookies.forEach(cookie -> map.put(cookie.name(), cookie.value()));
        return map;
    }

    @Override
    public Set<Cookie> cookiesRaw() {
        return this.cookies;
    }

    @Override
    public void download(@NonNull String fileName, @NonNull File file) throws Exception {
        if (!file.exists() || !file.isFile()) {
            throw new NotFoundException("Not found file: " + file.getPath());
        }
        String contentType = StringKit.mimeType(file.getName());
        headers.set("Content-Disposition", "attachment; filename=" + new String(fileName.getBytes("UTF-8"), "ISO8859_1"));
        headers.set(HttpConst.CONTENT_LENGTH, file.length());
        headers.set(HttpConst.CONTENT_TYPE_STRING, contentType);

        this.body = new StreamBody(new FileInputStream(file));
    }

    @Override
    public OutputStreamWrapper outputStream() throws IOException {
        File         file         = Files.createTempFile("blade", ".temp").toFile();
        OutputStream outputStream = new FileOutputStream(file);
        return new OutputStreamWrapper(outputStream, file);
    }

    @Override
    public void render(@NonNull ModelAndView modelAndView) {
        this.body = new ViewBody(modelAndView);
    }

    @Override
    public void redirect(@NonNull String newUri) {
        headers.set(HttpConst.LOCATION, newUri);
        this.status(302);
        this.body = EmptyBody.empty();
    }

    @Override
    public ModelAndView modelAndView() {
        if (this.body instanceof ViewBody) {
            return ((ViewBody) this.body).modelAndView();
        }
        throw new BladeException(500, "No view available");
    }

    public HttpResponse(Response response) {
        this.contentType = response.contentType();
        this.statusCode = response.statusCode();
        if (null != response.headers()) {
            response.headers().forEach(this.headers::add);
        }
        if (null != response.cookies()) {
            response.cookies().forEach((k, v) -> this.cookies.add(new DefaultCookie(k, v)));
        }
    }

    public HttpResponse(int code, String content) {
        this.statusCode = code;
        this.body(content);
    }

    public HttpResponse() {
    }

    @Override
    public Body body() {
        return this.body;
    }

    @Override
    public void body(Body body) {
        this.body = body;
    }
}