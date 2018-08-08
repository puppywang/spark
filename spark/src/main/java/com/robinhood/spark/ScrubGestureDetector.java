/**
 * Copyright (C) 2016 Robinhood Markets, Inc.
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

package com.robinhood.spark;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * Exposes simple methods for detecting scrub events.
 */
public class ScrubGestureDetector implements View.OnTouchListener {
    static final long LONG_PRESS_TIMEOUT_MS = 250;

    private final ScrubListener scrubListener;
    private final float touchSlop;
    private final Handler handler;

    private VelocityTracker velocityTracker;
    private MoveListener moveListener;
    private boolean enabled;
    private float downX, downY;
    private boolean isMoving;

    ScrubGestureDetector(
            @NonNull ScrubListener scrubListener,
            @NonNull Handler handler,
            float touchSlop) {
        this.scrubListener = scrubListener;
        this.handler = handler;
        this.touchSlop = touchSlop;
    }

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            scrubListener.onScrubbed(downX, downY);
        }
    };

    public void setMoveListener(MoveListener moveListener) {
        this.moveListener = moveListener;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!enabled) return false;

        final float x = event.getX();
        final float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // store the time to compute whether future events are 'long presses'
                downX = x;
                downY = y;
                isMoving = false;

                handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (isMoving && moveListener != null) {
                    float deltaX = x - downX;
                    float deltaY = y - downY;

                    moveListener.onMove(deltaX, deltaY);
                    downX = x;
                    downY = y;
                } else {
                    // calculate the elapsed time since the down event
                    float timeDelta = event.getEventTime() - event.getDownTime();

                    // if the user has intentionally long-pressed
                    if (timeDelta >= LONG_PRESS_TIMEOUT_MS) {
                        handler.removeCallbacks(longPressRunnable);
                        scrubListener.onScrubbed(x, y);
                    } else {
                        // if we moved before longpress, remove the callback if we exceeded the tap slop
                        float deltaX = x - downX;
                        float deltaY = y - downY;
                        if (Math.abs(deltaX) >= touchSlop || Math.abs(deltaY) >= touchSlop) {
                            handler.removeCallbacks(longPressRunnable);
/*
                            // We got a MOVE event that exceeded tap slop but before the long-press
                            // threshold, we don't care about this series of events anymore.
                            return false;
*/
                            // User is moving, we just take care of moving.
                            if (moveListener == null)
                                return false;

                            isMoving = true;
                            moveListener.onMove(deltaX, deltaY);
                            downX = x;
                            downY = y;
                        }
                    }
                }

                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isMoving = false;
                handler.removeCallbacks(longPressRunnable);
                scrubListener.onScrubEnded();
                return true;
            default:
                return false;
        }
    }

    interface ScrubListener {
        void onScrubbed(float x, float y);
        void onScrubEnded();
    }

    public interface MoveListener {
        void onMove(float xDelta, float yDelta);
    }
}

