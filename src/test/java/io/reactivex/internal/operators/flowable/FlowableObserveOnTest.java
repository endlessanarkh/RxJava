/**
 * Copyright 2016 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.*;
import org.mockito.InOrder;
import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.exceptions.*;
import io.reactivex.functions.*;
import io.reactivex.internal.subscriptions.BooleanSubscription;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.*;
import io.reactivex.subscribers.*;

public class FlowableObserveOnTest {

    /**
     * This is testing a no-op path since it uses Schedulers.immediate() which will not do scheduling.
     */
    @SuppressWarnings("deprecation")
    @Test
    @Ignore("immediate scheduler not supported")
    public void testObserveOn() {
        Subscriber<Integer> observer = TestHelper.mockSubscriber();
        Flowable.just(1, 2, 3).observeOn(Schedulers.immediate()).subscribe(observer);

        verify(observer, times(1)).onNext(1);
        verify(observer, times(1)).onNext(2);
        verify(observer, times(1)).onNext(3);
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testOrdering() throws InterruptedException {
//        Flowable<String> obs = Flowable.just("one", null, "two", "three", "four");
        // FIXME null values not allowed
        Flowable<String> obs = Flowable.just("one", "null", "two", "three", "four");

        Subscriber<String> observer = TestHelper.mockSubscriber();

        InOrder inOrder = inOrder(observer);
        TestSubscriber<String> ts = new TestSubscriber<String>(observer);

        obs.observeOn(Schedulers.computation()).subscribe(ts);

        ts.awaitTerminalEvent(1000, TimeUnit.MILLISECONDS);
        if (ts.errors().size() > 0) {
            for (Throwable t : ts.errors()) {
                t.printStackTrace();
            }
            fail("failed with exception");
        }

        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("null");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(1)).onNext("four");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testThreadName() throws InterruptedException {
        System.out.println("Main Thread: " + Thread.currentThread().getName());
        // FIXME null values not allowed
//        Flowable<String> obs = Flowable.just("one", null, "two", "three", "four");
        Flowable<String> obs = Flowable.just("one", "null", "two", "three", "four");

        Subscriber<String> observer = TestHelper.mockSubscriber();
        final String parentThreadName = Thread.currentThread().getName();

        final CountDownLatch completedLatch = new CountDownLatch(1);

        // assert subscribe is on main thread
        obs = obs.doOnNext(new Consumer<String>() {

            @Override
            public void accept(String s) {
                String threadName = Thread.currentThread().getName();
                System.out.println("Source ThreadName: " + threadName + "  Expected => " + parentThreadName);
                assertEquals(parentThreadName, threadName);
            }

        });

        // assert observe is on new thread
        obs.observeOn(Schedulers.newThread()).doOnNext(new Consumer<String>() {

            @Override
            public void accept(String t1) {
                String threadName = Thread.currentThread().getName();
                boolean correctThreadName = threadName.startsWith("RxNewThreadScheduler");
                System.out.println("ObserveOn ThreadName: " + threadName + "  Correct => " + correctThreadName);
                assertTrue(correctThreadName);
            }

        }).doAfterTerminate(new Action() {

            @Override
            public void run() {
                completedLatch.countDown();

            }
        }).subscribe(observer);

        if (!completedLatch.await(1000, TimeUnit.MILLISECONDS)) {
            fail("timed out waiting");
        }

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(5)).onNext(any(String.class));
        verify(observer, times(1)).onComplete();
    }

    @SuppressWarnings("deprecation")
    @Test
    @Ignore("immediate scheduler not supported")
    public void observeOnTheSameSchedulerTwice() {
        Scheduler scheduler = Schedulers.immediate();

        Flowable<Integer> o = Flowable.just(1, 2, 3);
        Flowable<Integer> o2 = o.observeOn(scheduler);

        @SuppressWarnings("unchecked")
        DefaultSubscriber<Object> observer1 = mock(DefaultSubscriber.class);
        @SuppressWarnings("unchecked")
        DefaultSubscriber<Object> observer2 = mock(DefaultSubscriber.class);

        InOrder inOrder1 = inOrder(observer1);
        InOrder inOrder2 = inOrder(observer2);

        o2.subscribe(observer1);
        o2.subscribe(observer2);

        inOrder1.verify(observer1, times(1)).onNext(1);
        inOrder1.verify(observer1, times(1)).onNext(2);
        inOrder1.verify(observer1, times(1)).onNext(3);
        inOrder1.verify(observer1, times(1)).onComplete();
        verify(observer1, never()).onError(any(Throwable.class));
        inOrder1.verifyNoMoreInteractions();

        inOrder2.verify(observer2, times(1)).onNext(1);
        inOrder2.verify(observer2, times(1)).onNext(2);
        inOrder2.verify(observer2, times(1)).onNext(3);
        inOrder2.verify(observer2, times(1)).onComplete();
        verify(observer2, never()).onError(any(Throwable.class));
        inOrder2.verifyNoMoreInteractions();
    }

    @Test
    public void observeSameOnMultipleSchedulers() {
        TestScheduler scheduler1 = new TestScheduler();
        TestScheduler scheduler2 = new TestScheduler();

        Flowable<Integer> o = Flowable.just(1, 2, 3);
        Flowable<Integer> o1 = o.observeOn(scheduler1);
        Flowable<Integer> o2 = o.observeOn(scheduler2);

        Subscriber<Object> observer1 = TestHelper.mockSubscriber();
        Subscriber<Object> observer2 = TestHelper.mockSubscriber();

        InOrder inOrder1 = inOrder(observer1);
        InOrder inOrder2 = inOrder(observer2);

        o1.subscribe(observer1);
        o2.subscribe(observer2);

        scheduler1.advanceTimeBy(1, TimeUnit.SECONDS);
        scheduler2.advanceTimeBy(1, TimeUnit.SECONDS);

        inOrder1.verify(observer1, times(1)).onNext(1);
        inOrder1.verify(observer1, times(1)).onNext(2);
        inOrder1.verify(observer1, times(1)).onNext(3);
        inOrder1.verify(observer1, times(1)).onComplete();
        verify(observer1, never()).onError(any(Throwable.class));
        inOrder1.verifyNoMoreInteractions();

        inOrder2.verify(observer2, times(1)).onNext(1);
        inOrder2.verify(observer2, times(1)).onNext(2);
        inOrder2.verify(observer2, times(1)).onNext(3);
        inOrder2.verify(observer2, times(1)).onComplete();
        verify(observer2, never()).onError(any(Throwable.class));
        inOrder2.verifyNoMoreInteractions();
    }

    /**
     * Confirm that running on a NewThreadScheduler uses the same thread for the entire stream
     */
    @Test
    public void testObserveOnWithNewThreadScheduler() {
        final AtomicInteger count = new AtomicInteger();
        final int _multiple = 99;

        Flowable.range(1, 100000).map(new Function<Integer, Integer>() {

            @Override
            public Integer apply(Integer t1) {
                return t1 * _multiple;
            }

        }).observeOn(Schedulers.newThread())
        .blockingForEach(new Consumer<Integer>() {

            @Override
            public void accept(Integer t1) {
                assertEquals(count.incrementAndGet() * _multiple, t1.intValue());
                // FIXME toBlocking methods run on the current thread
                String name = Thread.currentThread().getName();
                assertFalse("Wrong thread name: " + name, name.startsWith("Rx"));
            }

        });

    }

    /**
     * Confirm that running on a ThreadPoolScheduler allows multiple threads but is still ordered.
     */
    @Test
    public void testObserveOnWithThreadPoolScheduler() {
        final AtomicInteger count = new AtomicInteger();
        final int _multiple = 99;

        Flowable.range(1, 100000).map(new Function<Integer, Integer>() {

            @Override
            public Integer apply(Integer t1) {
                return t1 * _multiple;
            }

        }).observeOn(Schedulers.computation())
        .blockingForEach(new Consumer<Integer>() {

            @Override
            public void accept(Integer t1) {
                assertEquals(count.incrementAndGet() * _multiple, t1.intValue());
                // FIXME toBlocking methods run on the caller's thread
                String name = Thread.currentThread().getName();
                assertFalse("Wrong thread name: " + name, name.startsWith("Rx"));
            }

        });
    }

    /**
     * Attempts to confirm that when pauses exist between events, the ScheduledObserver
     * does not lose or reorder any events since the scheduler will not block, but will
     * be re-scheduled when it receives new events after each pause.
     * 
     * 
     * This is non-deterministic in proving success, but if it ever fails (non-deterministically)
     * it is a sign of potential issues as thread-races and scheduling should not affect output.
     */
    @Test
    public void testObserveOnOrderingConcurrency() {
        final AtomicInteger count = new AtomicInteger();
        final int _multiple = 99;

        Flowable.range(1, 10000).map(new Function<Integer, Integer>() {

            @Override
            public Integer apply(Integer t1) {
                if (randomIntFrom0to100() > 98) {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                return t1 * _multiple;
            }

        }).observeOn(Schedulers.computation())
        .blockingForEach(new Consumer<Integer>() {

            @Override
            public void accept(Integer t1) {
                assertEquals(count.incrementAndGet() * _multiple, t1.intValue());
//                assertTrue(name.startsWith("RxComputationThreadPool"));
                // FIXME toBlocking now runs its methods on the caller thread
                String name = Thread.currentThread().getName();
                assertFalse("Wrong thread name: " + name, name.startsWith("Rx"));
            }

        });
    }

    @Test
    public void testNonBlockingOuterWhileBlockingOnNext() throws InterruptedException {

        final CountDownLatch completedLatch = new CountDownLatch(1);
        final CountDownLatch nextLatch = new CountDownLatch(1);
        final AtomicLong completeTime = new AtomicLong();
        // use subscribeOn to make async, observeOn to move
        Flowable.range(1, 2).subscribeOn(Schedulers.newThread()).observeOn(Schedulers.newThread()).subscribe(new DefaultSubscriber<Integer>() {

            @Override
            public void onComplete() {
                System.out.println("onCompleted");
                completeTime.set(System.nanoTime());
                completedLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer t) {
                // don't let this thing finish yet
                try {
                    if (!nextLatch.await(1000, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("it shouldn't have timed out");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("it shouldn't have failed");
                }
            }

        });

        long afterSubscribeTime = System.nanoTime();
        System.out.println("After subscribe: " + completedLatch.getCount());
        assertEquals(1, completedLatch.getCount());
        nextLatch.countDown();
        completedLatch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(completeTime.get() > afterSubscribeTime);
        System.out.println("onComplete nanos after subscribe: " + (completeTime.get() - afterSubscribeTime));
    }

    private static int randomIntFrom0to100() {
        // XORShift instead of Math.random http://javamex.com/tutorials/random_numbers/xorshift.shtml
        long x = System.nanoTime();
        x ^= (x << 21);
        x ^= (x >>> 35);
        x ^= (x << 4);
        return Math.abs((int) x % 100);
    }

    @Test
    public void testDelayedErrorDeliveryWhenSafeSubscriberUnsubscribes() {
        TestScheduler testScheduler = new TestScheduler();

        Flowable<Integer> source = Flowable.concat(Flowable.<Integer> error(new TestException()), Flowable.just(1));

        @SuppressWarnings("unchecked")
        DefaultSubscriber<Integer> o = mock(DefaultSubscriber.class);
        InOrder inOrder = inOrder(o);

        source.observeOn(testScheduler).subscribe(o);

        inOrder.verify(o, never()).onError(any(TestException.class));

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS);

        inOrder.verify(o).onError(any(TestException.class));
        inOrder.verify(o, never()).onNext(anyInt());
        inOrder.verify(o, never()).onComplete();
    }

    @Test
    public void testAfterUnsubscribeCalledThenObserverOnNextNeverCalled() {
        final TestScheduler testScheduler = new TestScheduler();

        final Subscriber<Integer> observer = TestHelper.mockSubscriber();
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(observer);
        
        Flowable.just(1, 2, 3)
                .observeOn(testScheduler)
                .subscribe(ts);
        
        ts.dispose();
        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS);

        final InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, never()).onNext(anyInt());
        inOrder.verify(observer, never()).onError(any(Exception.class));
        inOrder.verify(observer, never()).onComplete();
    }

    @Test
    public void testBackpressureWithTakeAfter() {
        final AtomicInteger generated = new AtomicInteger();
        Flowable<Integer> flowable = Flowable.fromIterable(new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {

                    @Override
                    public void remove() {
                    }

                    @Override
                    public Integer next() {
                        return generated.getAndIncrement();
                    }

                    @Override
                    public boolean hasNext() {
                        return true;
                    }
                };
            }
        });

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                System.err.println("c t = " + t + " thread " + Thread.currentThread());
                super.onNext(t);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
        };

        flowable
                .observeOn(Schedulers.newThread())
                .take(3)
                .subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        System.err.println(testSubscriber.values());
        testSubscriber.assertValues(0, 1, 2);
        // it should be between the take num and requested batch size across the async boundary
        System.out.println("Generated: " + generated.get());
        assertTrue(generated.get() >= 3 && generated.get() <= Flowable.bufferSize());
    }

    @Test
    public void testBackpressureWithTakeAfterAndMultipleBatches() {
        int numForBatches = Flowable.bufferSize() * 3 + 1; // should be 4 batches == ((3*n)+1) items
        final AtomicInteger generated = new AtomicInteger();
        Flowable<Integer> flowable = Flowable.fromIterable(new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {

                    @Override
                    public void remove() {
                    }

                    @Override
                    public Integer next() {
                        return generated.getAndIncrement();
                    }

                    @Override
                    public boolean hasNext() {
                        return true;
                    }
                };
            }
        });

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                //                System.err.println("c t = " + t + " thread " + Thread.currentThread());
                super.onNext(t);
            }
        };

        flowable
                .observeOn(Schedulers.newThread())
                .take(numForBatches)
                .subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        System.err.println(testSubscriber.values());
        // it should be between the take num and requested batch size across the async boundary
        System.out.println("Generated: " + generated.get());
        assertTrue(generated.get() >= numForBatches && generated.get() <= numForBatches + Flowable.bufferSize());
    }

    @Test
    public void testBackpressureWithTakeBefore() {
        final AtomicInteger generated = new AtomicInteger();
        Flowable<Integer> flowable = Flowable.fromIterable(new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {

                    @Override
                    public void remove() {
                    }

                    @Override
                    public Integer next() {
                        return generated.getAndIncrement();
                    }

                    @Override
                    public boolean hasNext() {
                        return true;
                    }
                };
            }
        });

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();
        flowable
                .take(7)
                .observeOn(Schedulers.newThread())
                .subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertValues(0, 1, 2, 3, 4, 5, 6);
        assertEquals(7, generated.get());
    }

    @Test
    public void testQueueFullEmitsError() {
        final CountDownLatch latch = new CountDownLatch(1);
        Flowable<Integer> flowable = Flowable.unsafeCreate(new Publisher<Integer>() {

            @Override
            public void subscribe(Subscriber<? super Integer> o) {
                o.onSubscribe(new BooleanSubscription());
                for (int i = 0; i < Flowable.bufferSize() + 10; i++) {
                    o.onNext(i);
                }
                latch.countDown();
                o.onComplete();
            }

        });

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>(new DefaultSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer t) {
                // force it to be slow and wait until we have queued everything
                try {
                    latch.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        });
        flowable.observeOn(Schedulers.newThread()).subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent();
        List<Throwable> errors = testSubscriber.errors();
        assertEquals(1, errors.size());
        System.out.println("Errors: " + errors);
        Throwable t = errors.get(0);
        if (t instanceof MissingBackpressureException) {
            // success, we expect this
        } else {
            if (t.getCause() instanceof MissingBackpressureException) {
                // this is also okay
            } else {
                fail("Expecting MissingBackpressureException");
            }
        }
    }

    @Test
    public void testAsyncChild() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(0, 100000).observeOn(Schedulers.newThread()).observeOn(Schedulers.newThread()).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
    }

    @Test
    public void testOnErrorCutsAheadOfOnNext() {
        for (int i = 0; i < 50; i++) {
            final PublishProcessor<Long> subject = PublishProcessor.create();
    
            final AtomicLong counter = new AtomicLong();
            TestSubscriber<Long> ts = new TestSubscriber<Long>(new DefaultSubscriber<Long>() {
    
                @Override
                public void onComplete() {
    
                }
    
                @Override
                public void onError(Throwable e) {
    
                }
    
                @Override
                public void onNext(Long t) {
                    // simulate slow consumer to force backpressure failure
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                    }
                }
    
            });
            subject.observeOn(Schedulers.computation()).subscribe(ts);
    
            // this will blow up with backpressure
            while (counter.get() < 102400) {
                subject.onNext(counter.get());
                counter.incrementAndGet();
            }
    
            ts.awaitTerminalEvent();
            assertEquals(1, ts.errors().size());
            ts.assertError(MissingBackpressureException.class);
            // assert that the values are sequential, that cutting in didn't allow skipping some but emitting others.
            // example [0, 1, 2] not [0, 1, 4]
            List<Long> onNextEvents = ts.values();
            assertTrue(onNextEvents.isEmpty() || onNextEvents.size() == onNextEvents.get(onNextEvents.size() - 1) + 1);
            // we should emit the error without emitting the full buffer size
            assertTrue(onNextEvents.size() < Flowable.bufferSize());
        }
    }

    /**
     * Make sure we get a MissingBackpressureException propagated through when we have a fast temporal (hot) producer.
     */
    @Test
    public void testHotOperatorBackpressure() {
        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.interval(0, 1, TimeUnit.MICROSECONDS)
                .observeOn(Schedulers.computation())
                .map(new Function<Long, String>() {

                    @Override
                    public String apply(Long t1) {
                        System.out.println(t1);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                        return t1 + " slow value";
                    }

                }).subscribe(ts);

        ts.awaitTerminalEvent();
        System.out.println("Errors: " + ts.errors());
        assertEquals(1, ts.errors().size());
        assertEquals(MissingBackpressureException.class, ts.errors().get(0).getClass());
    }

    @Test
    public void testErrorPropagatesWhenNoOutstandingRequests() {
        Flowable<Long> timer = Flowable.interval(0, 1, TimeUnit.MICROSECONDS)
                .doOnEach(new Consumer<Notification<Long>>() {

                    @Override
                    public void accept(Notification<Long> n) {
//                        System.out.println("BEFORE " + n);
                    }

                })
                .observeOn(Schedulers.newThread())
                .doOnEach(new Consumer<Notification<Long>>() {

                    @Override
                    public void accept(Notification<Long> n) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
//                        System.out.println("AFTER " + n);
                    }

                });

        TestSubscriber<Long> ts = new TestSubscriber<Long>();

        Flowable.combineLatest(timer, Flowable.<Integer> never(), new BiFunction<Long, Integer, Long>() {

            @Override
            public Long apply(Long t1, Integer t2) {
                return t1;
            }

        }).take(Flowable.bufferSize() * 2).subscribe(ts);

        ts.awaitTerminalEvent();
        assertEquals(1, ts.errors().size());
        assertEquals(MissingBackpressureException.class, ts.errors().get(0).getClass());
    }

    @Test
    public void testRequestOverflow() throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();
        Flowable.range(1, 100).observeOn(Schedulers.computation())
                .subscribe(new DefaultSubscriber<Integer>() {

                    boolean first = true;
                    
                    @Override
                    public void onStart() {
                        request(2);
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Integer t) {
                        count.incrementAndGet();
                        if (first) {
                            request(Long.MAX_VALUE - 1);
                            request(Long.MAX_VALUE - 1);
                            request(10);
                            first = false;
                        }
                    }
                });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(100, count.get());

    }
    
    @Test
    public void testNoMoreRequestsAfterUnsubscribe() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<Long> requests = Collections.synchronizedList(new ArrayList<Long>());
        Flowable.range(1, 1000000)
                .doOnRequest(new LongConsumer() {

                    @Override
                    public void accept(long n) {
                        requests.add(n);
                    }
                })
                .observeOn(Schedulers.io())
                .subscribe(new DefaultSubscriber<Integer>() {

                    @Override
                    public void onStart() {
                        request(1);
                    }

                    @Override
                    public void onComplete() {
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(Integer t) {
                        cancel();
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        // FIXME observeOn requests bufferSize at first always
        assertEquals(Arrays.asList(128L), requests);
    }

    
    @Test
    public void testErrorDelayed() {
        TestScheduler s = Schedulers.test();
        
        Flowable<Integer> source = Flowable.just(1, 2 ,3)
                .concatWith(Flowable.<Integer>error(new TestException()));
        
        TestSubscriber<Integer> ts = TestSubscriber.create(0);

        source.observeOn(s, true).subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNotComplete();
        
        s.advanceTimeBy(1, TimeUnit.SECONDS);

        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNotComplete();

        ts.request(1);
        s.advanceTimeBy(1, TimeUnit.SECONDS);
        
        ts.assertValues(1);
        ts.assertNoErrors();
        ts.assertNotComplete();
        
        ts.request(3); // requesting 2 doesn't switch to the error() source for some reason in concat.
        s.advanceTimeBy(1, TimeUnit.SECONDS);
        
        ts.assertValues(1, 2, 3);
        ts.assertError(TestException.class);
        ts.assertNotComplete();
    }
    
    @Test
    public void testErrorDelayedAsync() {
        Flowable<Integer> source = Flowable.just(1, 2 ,3)
                .concatWith(Flowable.<Integer>error(new TestException()));
        
        TestSubscriber<Integer> ts = TestSubscriber.create();

        source.observeOn(Schedulers.computation(), true).subscribe(ts);
        
        ts.awaitTerminalEvent(2, TimeUnit.SECONDS);
        ts.assertValues(1, 2, 3);
        ts.assertError(TestException.class);
        ts.assertNotComplete();
    }
    
    @Test
    public void requestExactCompletesImmediately() {
        TestSubscriber<Integer> ts = TestSubscriber.create(0);
        
        TestScheduler test = Schedulers.test();

        Flowable.range(1, 10).observeOn(test).subscribe(ts);

        test.advanceTimeBy(1, TimeUnit.SECONDS);

        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNotComplete();
        
        ts.request(10);

        test.advanceTimeBy(1, TimeUnit.SECONDS);
        
        ts.assertValueCount(10);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    @Test
    public void fixedReplenishPattern() {
        TestSubscriber<Integer> ts = TestSubscriber.create(0);

        TestScheduler test = Schedulers.test();
        
        final List<Long> requests = new ArrayList<Long>();
        
        Flowable.range(1, 100)
        .doOnRequest(new LongConsumer() {
            @Override
            public void accept(long v) {
                requests.add(v);
            }
        })
        .observeOn(test, false, 16).subscribe(ts);
        
        test.advanceTimeBy(1, TimeUnit.SECONDS);
        ts.request(20);
        test.advanceTimeBy(1, TimeUnit.SECONDS);
        ts.request(10);
        test.advanceTimeBy(1, TimeUnit.SECONDS);
        ts.request(50);
        test.advanceTimeBy(1, TimeUnit.SECONDS);
        ts.request(35);
        test.advanceTimeBy(1, TimeUnit.SECONDS);
        
        ts.assertValueCount(100);
        ts.assertComplete();
        ts.assertNoErrors();
        
        assertEquals(Arrays.asList(16L, 12L, 12L, 12L, 12L, 12L, 12L, 12L, 12L), requests);
    }
    
    @Test
    public void bufferSizesWork() {
        for (int i = 1; i <= 1024; i = i * 2) {
            TestSubscriber<Integer> ts = TestSubscriber.create();
            
            Flowable.range(1, 1000 * 1000).observeOn(Schedulers.computation(), false, i)
            .subscribe(ts);
            
            ts.awaitTerminalEvent();
            ts.assertValueCount(1000 * 1000);
            ts.assertComplete();
            ts.assertNoErrors();
        }
    }
    
    @Test
    public void synchronousRebatching() {
        final List<Long> requests = new ArrayList<Long>();
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
            
        Flowable.range(1, 50)
        .doOnRequest(new LongConsumer() {
            @Override
            public void accept(long r) {
                requests.add(r);
            }
        })
       .rebatchRequests(20)
       .subscribe(ts);
       
       ts.assertValueCount(50);
       ts.assertNoErrors();
       ts.assertComplete();
       
       assertEquals(Arrays.asList(20L, 15L, 15L, 15L), requests);
    }
    
    @Test
    public void rebatchRequestsArgumentCheck() {
        try {
            Flowable.never().rebatchRequests(-99);
            fail("Didn't throw IAE");
        } catch (IllegalArgumentException ex) {
            assertEquals("bufferSize > 0 required but it was -99", ex.getMessage());
        }
    }
}