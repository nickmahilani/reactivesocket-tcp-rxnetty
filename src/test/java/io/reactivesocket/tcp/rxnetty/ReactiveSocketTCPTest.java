/**
 * Copyright 2015 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivesocket.tcp.rxnetty;

import static rx.Observable.just;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.tcp.client.TcpClient;
import io.reactivex.netty.protocol.tcp.server.TcpServer;
import rx.Single;

public class ReactiveSocketTCPTest {

    @Test
    public void test() {
        // create protocol with handlers
        ReactiveSocketTCP reactiveSocketTCP = ReactiveSocketTCP.create(
                requestResponse -> {
                    return Single.just("hello" + requestResponse);
                } , requestStream -> {
                    return just("a_" + requestStream, "b_" + requestStream);
                } , null, null);

        // start server with protocol
        TcpServer<ByteBuf, ByteBuf> server = TcpServer.newServer();
        int port = server.getServerPort();
        server.start(reactiveSocketTCP::acceptConnection);

        // TODO send actual requests
        TcpClient.newClient("localhost", port)
                .createConnectionRequest()
                .flatMap(connection -> {
                    return connection.getInput();
                });

        server.shutdown();
    }
}
