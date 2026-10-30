package com.hibegin.common.util.http;

import com.zrlog.util.ExecutionDeadline;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Keeps headers, the complete body and cancellation inside the caller's budget. */
public final class BoundedHttpExchange {
    private BoundedHttpExchange() {
    }

    /** Keeps the caller's client configuration while bounding the complete buffered response. */
    public static HttpResponse<byte[]> sendBytes(HttpClient client, HttpRequest request,
                                                  ExecutionDeadline deadline, long maxBytes)
            throws IOException, InterruptedException {
        return send(client, request, HttpResponse.BodySubscribers::ofByteArray, maxBytes, deadline);
    }

    static <T> HttpResponse<T> send(HttpClient client, HttpRequest request,
                                     Supplier<HttpResponse.BodySubscriber<T>> bodyFactory, long maxBytes,
                                     ExecutionDeadline deadline) throws IOException, InterruptedException {
        if (maxBytes <= 0) throw new IllegalArgumentException("Response size limit must be positive");
        if (request.timeout().isPresent()) {
            deadline = deadline.capMillis(Math.min(TimeUnit.DAYS.toMillis(1), request.timeout().get().toMillis()));
        }
        deadline.requireRemainingMillis();
        Body<T> body = new Body<>(bodyFactory, maxBytes);
        CompletableFuture<HttpResponse<T>> transfer = null;
        try {
            transfer = client.sendAsync(request, body);
            HttpResponse<T> response = transfer.get(deadline.requireRemainingMillis(), TimeUnit.MILLISECONDS);
            deadline.requireRemainingMillis();
            return response;
        } catch (Exception failure) {
            // Serialize cancellation with body creation and writes before the caller deletes a staging file.
            body.cancel(failure);
            if (transfer != null) transfer.cancel(true);
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw (InterruptedException) failure;
            }
            if (failure instanceof ExecutionException && failure.getCause() instanceof Exception) {
                failure = (Exception) failure.getCause();
            }
            if (failure instanceof TimeoutException) throw new HttpTimeoutException("HTTP time budget exhausted");
            if (failure instanceof IOException) throw (IOException) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            throw new IOException("HTTP exchange failed", failure);
        }
    }

    private static final class Body<T> implements HttpResponse.BodyHandler<T> {
        private final Supplier<HttpResponse.BodySubscriber<T>> factory;
        private final long maxBytes;
        private Subscriber<T> subscriber;
        private boolean cancelled;

        private Body(Supplier<HttpResponse.BodySubscriber<T>> factory, long maxBytes) {
            this.factory = factory;
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo info) {
            if (cancelled) throw new CancellationException();
            subscriber = new Subscriber<>(factory.get(), maxBytes);
            return subscriber;
        }

        synchronized void cancel(Throwable failure) {
            cancelled = true;
            if (subscriber != null) subscriber.abort(failure);
        }
    }

    private static final class Subscriber<T> implements HttpResponse.BodySubscriber<T> {
        private final HttpResponse.BodySubscriber<T> delegate;
        private final long maxBytes;
        private Flow.Subscription subscription;
        private long received;
        private boolean done;

        private Subscriber(HttpResponse.BodySubscriber<T> delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        public CompletionStage<T> getBody() { return delegate.getBody(); }

        public synchronized void onSubscribe(Flow.Subscription subscription) {
            if (done) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (done) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maxBytes - received) {
                    abort(new IOException("HTTP response exceeds " + maxBytes + " bytes"));
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
        }

        public synchronized void onComplete() {
            if (done) return;
            done = true;
            delegate.onComplete();
        }

        public synchronized void onError(Throwable failure) {
            if (done) return;
            done = true;
            delegate.onError(failure);
        }

        synchronized void abort(Throwable failure) {
            if (done) return;
            done = true;
            if (subscription != null) subscription.cancel();
            delegate.onError(failure);
        }
    }
}
