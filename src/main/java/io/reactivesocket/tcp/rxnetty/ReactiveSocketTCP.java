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

import static rx.RxReactiveStreams.toObservable;
import static rx.RxReactiveStreams.toPublisher;

import org.reactivestreams.Publisher;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivesocket.DuplexConnection;
import io.reactivesocket.Message;
import io.reactivesocket.ReactiveSocketServerProtocol;
import io.reactivesocket.RequestHandler;
import io.reactivex.netty.channel.Connection;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;

/**
 * A ReactiveSocket handler for TCP in RxNetty.
 * <p>
 * Use this ReactiveSocketTCP with an RxNetty server similar to below.
 * 
 * <pre>
 * {@code
 *	ReactiveSocketTCP reactiveSocketTCP = ReactiveSocketTCP.create(
 * request -> {
 * // handler logic for request/response (return a Single)
 * return Single.just("hello" + request);
 * },
 * request -> {
 * // handler logic for request/stream (return an Observable)
 * return just("a_" + request, "b_" + request);
 * });
 *
 * // start server with protocol
 * TcpServer.newServer().start(reactiveSocketTCP::acceptConnection);
 * </pre>
 */
public class ReactiveSocketTCP {

    private final ReactiveSocketServerProtocol rsProtocol;

    private ReactiveSocketTCP(
            Func1<String, Single<String>> requestResponseHandler,
            Func1<String, Observable<String>> requestStreamHandler,
            Func1<String, Observable<String>> requestSubscriptionHandler,
            Func1<String, Observable<Void>> fireAndForgetHandler) {
        this(new RequestHandler() {

            @Override
            public Publisher<String> handleRequestResponse(String request) {
                return toPublisher(requestResponseHandler.call(request).toObservable());
            }

            @Override
            public Publisher<String> handleRequestStream(String request) {
                return toPublisher(requestStreamHandler.call(request));
            }

            @Override
            public Publisher<String> handleRequestSubscription(String request) {
                return toPublisher(requestSubscriptionHandler.call(request));
            }

            @Override
            public Publisher<Void> handleFireAndForget(String request) {
                return toPublisher(fireAndForgetHandler.call(request));
            }

        });
    }

    private ReactiveSocketTCP(RequestHandler requestHandler) {
        // instantiate the ServerProtocol with handler converters from Observable to Publisher
        this.rsProtocol = ReactiveSocketServerProtocol.create(requestHandler);
    }

    public static ReactiveSocketTCP create(
            Func1<String, Single<String>> requestResponseHandler,
            Func1<String, Observable<String>> requestStreamHandler,
            Func1<String, Observable<String>> requestSubscriptionHandler,
            Func1<String, Observable<Void>> fireAndForgetHandler) {
        return new ReactiveSocketTCP(requestResponseHandler, requestStreamHandler, requestSubscriptionHandler, fireAndForgetHandler);
    }

    public static ReactiveSocketTCP create(RequestHandler handler) {
        return new ReactiveSocketTCP(handler);
    }

    /**
     * Use this method as the RxNetty TCP connection handler.
     * 
     * @param ws
     * @return
     */
    public Observable<Void> acceptConnection(Connection<ByteBuf, ByteBuf> tcp) {
        return toObservable(rsProtocol.acceptConnection(new DuplexConnection() {

            @Override
            public Publisher<Message> getInput() {
                // TODO need to do framing
                return toPublisher(tcp.getInput().map(data -> {
                    // TODO is this copying bytes?
                    try {
                        return Message.from(data.nioBuffer());
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }));
            }

            @Override
            public Publisher<Void> write(Publisher<Message> o) {
                return toPublisher(tcp.write(toObservable(o).map(m -> {
                    return Unpooled.wrappedBuffer(m.getBytes());
                })));
            }

        }));
    }

}
