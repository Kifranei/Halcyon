package com.ella.music.shizuku;

/** Binder interface exposed by the Shizuku user service. */
interface IShizukuShellService {
    String exec(String command);
    void destroy();
}
