/*
 * Copyright (c) 2015. Anders Nielsen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package dk.siman.jive.httpd;

import android.text.TextUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class CastFileServer extends FileServer {

    public CastFileServer() {
        super();
    }

    public CastFileServer(int port) {
        super(port);
    }

    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> infos = NetworkInterface
                    .getNetworkInterfaces();
            while (infos.hasMoreElements()) {
                NetworkInterface niFace = infos.nextElement();
                Enumeration<InetAddress> enumIpAddr = niFace.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    InetAddress mInetAddress = enumIpAddr.nextElement();
                    if (!mInetAddress.isLoopbackAddress()
                            && mInetAddress instanceof Inet4Address) {
                        return mInetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String cropFilePath(String uri) {
        //String divider = new StringBuilder(":").append(serverPort).toString();
        return uri.trim().replace(File.separatorChar, '/');
    }

    public final String generateUri(String localPath) {
        if (TextUtils.isEmpty(localPath)) {
            return null;
        }
        return "http://" + getLocalIpAddress() + ":" + getServerPort() + localPath;
    }
}
