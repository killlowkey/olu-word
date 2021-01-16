package com.example;

import cn.hutool.core.swing.clipboard.ClipboardUtil;
import cn.hutool.http.HttpUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * @author Ray
 * @date created in 2021/1/3 11:43:01
 */
public class OluWordApplication {

    private static String currentWord = "";
    private static String authorization;

    static {
        Mono.just(new Properties())
                .doOnNext(properties -> {
                    try {
                        properties.load(new FileInputStream(getPath() + "config.properties"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                })
                .subscribe(properties -> authorization = properties.getProperty("authorization"));
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Flux.interval(Duration.ofMillis(200))
                .map(seed -> getSysClipboardText())
                .filter(word -> !word.equals(currentWord) && word.matches("[a-zA-Z][a-z]+"))
                .publishOn(Schedulers.parallel())
                .doOnNext(word -> currentWord = word)
                .doOnSubscribe(subscription -> System.out.println("欧路词典监控已启动..."))
                .doOnComplete(countDownLatch::countDown)
                .subscribe(OluWordApplication::submitWord);

        countDownLatch.await();
    }

    private static void submitWord(String word) {
        Mono.just(HttpUtil.createPost("https://api.frdic.com/api/open/v1/studylist/words"))
                .doOnNext(request -> request
                        .header("Authorization", authorization)
                        .body(String.format("{\"id\":\"0\",\"language\":\"en\",\"words\":[\"%s\"]}",
                                word.toLowerCase())))
                .map(request -> request.execute().getStatus())
                .subscribe(status -> {
                    if (status == 201) {
                        System.out.println(word.toLowerCase() + "：导入成功");
                    } else if (status == 401) {
                        System.out.println("授权过期，请更新Authorization，并重启应用");
                    }
                });
    }

    private static String getSysClipboardText() {
        String clipboard = ClipboardUtil.getStr();
        return clipboard != null ? clipboard.trim() : "";
    }

    private static String getPath() {
        String path = OluWordApplication.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        int index = 0;

        char[] chars = path.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '/' || c == '\\') {
                index = i;
            }
        }

        return path.substring(0, index + 1);
    }
}
