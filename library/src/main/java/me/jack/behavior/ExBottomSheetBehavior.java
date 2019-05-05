package me.jack.behavior;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.math.MathUtils;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.AbsSavedState;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;

/**
 * Created by Jack on 2019/5/5.
 */
public class ExBottomSheetBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {
    private final String TAG = ExBottomSheetBehavior.class.getSimpleName();

    /**
     * The bottom sheet is dragging.
     */
    public static final int STATE_DRAGGING = 1;

    /**
     * The bottom sheet is settling.
     */
    public static final int STATE_SETTLING = 2;

    /**
     * The bottom sheet is expanded.
     */
    public static final int STATE_EXPANDED = 3;

    /**
     * The bottom sheet is collapsed.
     */
    public static final int STATE_COLLAPSED = 4;

    /**
     * The bottom sheet is hidden.
     */
    public static final int STATE_HIDDEN = 5;

    /**
     * The bottom sheet is peek.
     */
    public static final int STATE_PEEK = 6;

    private static final int PEEK_HEIGHT_AUTO = -1;

    private static final int HANDLE_HEIGHT_DEFAULT = 0;

    private static final float HIDE_THRESHOLD = 0.5f;

    private static final float HIDE_FRICTION = 0.1f;

    private float mMaximumVelocity;

    private int mPeekHeight;

    private int mHandleHeight;

    private boolean mPeekHeightAuto;

    private int mPeekHeightMin;

    private int mPeekOffset;

    private int mMinOffset;

    private int mMaxOffset;

    private int mHandleOffset;

    private boolean mHideable;

    private boolean mSkipCollapsed;

    private int mState = STATE_COLLAPSED;

    private ViewDragHelper mViewDragHelper;

    private boolean mIgnoreEvents;

    private int mLastNestedScrollDy;

    private boolean mNestedScrolled;

    private int mParentHeight;

    private WeakReference<V> mViewRef;

    private WeakReference<View> mNestedScrollingChildRef;

    private VelocityTracker mVelocityTracker;

    private int mActivePointerId;

    private int mInitialY;

    private boolean mTouchingScrollingChild;

    private OnStateChangedListener mStateChangedListener;

    private OnSlideListener mSlideListener;

    public ExBottomSheetBehavior() {
    }

    public ExBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ExBottomSheetBehavior);

        TypedValue value = a.peekValue(R.styleable.ExBottomSheetBehavior_bsb_peekHeight);
        if (value != null && value.data == PEEK_HEIGHT_AUTO) {
            setPeekHeight(value.data);
        } else {
            setPeekHeight(a.getDimensionPixelSize(
                    R.styleable.ExBottomSheetBehavior_bsb_peekHeight, PEEK_HEIGHT_AUTO));
        }

        setHandleHeight(a.getDimensionPixelSize(
                R.styleable.ExBottomSheetBehavior_bsb_handleHeight, HANDLE_HEIGHT_DEFAULT));

        setHideable(a.getBoolean(R.styleable.ExBottomSheetBehavior_bsb_hideable, false));

        setSkipCollapsed(a.getBoolean(R.styleable.ExBottomSheetBehavior_bsb_skipCollapsed, false));

        a.recycle();

        ViewConfiguration configuration = ViewConfiguration.get(context);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    @Override
    public Parcelable onSaveInstanceState(CoordinatorLayout parent, V child) {
        return new SavedState(super.onSaveInstanceState(parent, child), mState);
    }

    @Override
    public void onRestoreInstanceState(CoordinatorLayout parent, V child, Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(parent, child, ss.getSuperState());
        if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
            mState = STATE_COLLAPSED;
        } else {
            mState = ss.state;
        }
    }

    @SuppressLint("PrivateResource")
    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, V child, int layoutDirection) {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            child.setFitsSystemWindows(true);
        }
        int savedTop = child.getTop();
        // First let the parent lay it out
        parent.onLayoutChild(child, layoutDirection);
        // Offset the bottom sheet
        mParentHeight = parent.getHeight();

        int peekHeight;
        if (mPeekHeightAuto) {
            if (mPeekHeightMin == 0) {
                mPeekHeightMin = parent.getResources().getDimensionPixelSize(R.dimen.design_bottom_sheet_peek_height_min);
            }
            peekHeight = Math.max(mPeekHeightMin, mParentHeight - parent.getWidth() * 9 / 16);
        } else {
            peekHeight = mPeekHeight;
        }

        int handleHeight = mHandleHeight;

        if (peekHeight < handleHeight) {
            throw new IllegalStateException("peekHeight should be greater than or equal to handleHeight.");
        }

        mMinOffset = Math.max(0, mParentHeight - child.getHeight());
        mPeekOffset = Math.max(mParentHeight - peekHeight, mMinOffset);
        mMaxOffset = mHandleOffset = Math.max(mParentHeight - handleHeight, mMinOffset);

        int childTop = child.getTop();

        if (mState == STATE_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, mMinOffset - childTop);
        } else if (mHideable && mState == STATE_HIDDEN) {
            ViewCompat.offsetTopAndBottom(child, mParentHeight - childTop);
        } else if (mState == STATE_PEEK) {
            ViewCompat.offsetTopAndBottom(child, mPeekOffset - childTop);
        } else if (mState == STATE_COLLAPSED) {
            ViewCompat.offsetTopAndBottom(child, mHandleOffset - childTop);
        } else if (mState == STATE_DRAGGING || mState == STATE_SETTLING) {
            ViewCompat.offsetTopAndBottom(child, savedTop - child.getTop());
        }
        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback);
        }
        mViewRef = new WeakReference<>(child);
        mNestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));

        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            mIgnoreEvents = true;
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchingScrollingChild = false;
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                // Reset the ignore flag
                if (mIgnoreEvents) {
                    mIgnoreEvents = false;
                    return false;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                int initialX = (int) event.getX();
                mInitialY = (int) event.getY();
                View scroll = mNestedScrollingChildRef != null
                        ? mNestedScrollingChildRef.get() : null;
                if (scroll != null && parent.isPointInChildBounds(scroll, initialX, mInitialY)) {
                    mActivePointerId = event.getPointerId(event.getActionIndex());
                    mTouchingScrollingChild = true;
                }
                mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID &&
                        !parent.isPointInChildBounds(child, initialX, mInitialY);
                break;
        }
        if (!mIgnoreEvents && mViewDragHelper.shouldInterceptTouchEvent(event)) {
            return true;
        }
        View scroll = mNestedScrollingChildRef.get();
        return action == MotionEvent.ACTION_MOVE && scroll != null &&
                !mIgnoreEvents && mState != STATE_DRAGGING &&
                !parent.isPointInChildBounds(scroll, (int) event.getX(), (int) event.getY()) &&
                Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop();
    }

    @Override
    public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            return false;
        }
        int action = event.getActionMasked();
        if (mState == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (mViewDragHelper != null) {
            mViewDragHelper.processTouchEvent(event);
        }
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
            if (Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop()) {
                mViewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));
            }
        }
        return !mIgnoreEvents;
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child,
                                       @NonNull View directTargetChild, @NonNull View target,
                                       int axes, int type) {
        mLastNestedScrollDy = 0;
        mNestedScrolled = false;
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onStopNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child,
                                   @NonNull View target, int type) {
        if (child.getTop() == mMinOffset) {
            setStateInternal(STATE_EXPANDED);
            return;
        }
        if (mNestedScrollingChildRef == null || target != mNestedScrollingChildRef.get()
                || !mNestedScrolled) {
            return;
        }
        int top;
        int targetState;
        int childTop = child.getTop();
        if (mLastNestedScrollDy > 0) { // Upward
            if (childTop >= mPeekOffset) {
                top = mPeekOffset;
                targetState = STATE_PEEK;
            } else {
                top = mMinOffset;
                targetState = STATE_EXPANDED;
            }
        } else if (mHideable && shouldHide(child, getYVelocity())) {
            top = mParentHeight;
            targetState = STATE_HIDDEN;
        } else if (mLastNestedScrollDy == 0) {
            if (Math.abs(childTop - mMinOffset) < Math.abs(childTop - mPeekOffset)) {
                top = mMinOffset;
                targetState = STATE_EXPANDED;
            } else {
                top = mPeekOffset;
                targetState = STATE_PEEK;
            }
        } else {
            if (Math.abs(childTop - mPeekOffset) < Math.abs(childTop - mHandleOffset)) {
                top = mPeekOffset;
                targetState = STATE_PEEK;
            } else {
                top = mHandleOffset;
                targetState = STATE_COLLAPSED;
            }
        }
        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, targetState));
        } else {
            setStateInternal(targetState);
        }
        mNestedScrolled = false;
    }

    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child,
                                  @NonNull View target, int dx, int dy,
                                  @NonNull int[] consumed, int type) {
        View scrollingChild = mNestedScrollingChildRef.get();
        if (target != scrollingChild) {
            return;
        }
        int currentTop = child.getTop();
        int newTop = currentTop - dy;
        if (dy > 0) { // Upward
            if (newTop < mMinOffset) {
                consumed[1] = currentTop - mMinOffset;
                ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                setStateInternal(STATE_EXPANDED);
            } else {
                consumed[1] = dy;
                ViewCompat.offsetTopAndBottom(child, -dy);
                setStateInternal(STATE_DRAGGING);
            }
        } else if (dy < 0) { // Downward
            if (!target.canScrollVertically(-1)) {
                if (newTop <= mMaxOffset || mHideable) {
                    consumed[1] = dy;
                    ViewCompat.offsetTopAndBottom(child, -dy);
                    setStateInternal(STATE_DRAGGING);
                } else {
                    consumed[1] = currentTop - mMaxOffset;
                    ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                    setStateInternal(STATE_COLLAPSED);
                }
            }
        }
        dispatchOnSlide(child.getTop());
        mLastNestedScrollDy = dy;
        mNestedScrolled = true;
    }

    @Override
    public boolean onNestedPreFling(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child,
                                    @NonNull View target, float velocityX, float velocityY) {
        return target == mNestedScrollingChildRef.get() &&
                (mState != STATE_EXPANDED ||
                        super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY));
    }

    /* public */

    public final void setState(final int state) {
        if (state == mState) {
            return;
        }
        if (mViewRef == null) {
            // The view is not laid out yet; modify mState and let onLayoutChild handle it later
            if (state == STATE_COLLAPSED || state == STATE_EXPANDED || state == STATE_PEEK ||
                    (mHideable && state == STATE_HIDDEN)) {
                mState = state;
            }
            return;
        }
        final V child = mViewRef.get();
        if (child == null) {
            return;
        }
        // Start the animation; wait until a pending layout if there is one.
        ViewParent parent = child.getParent();
        if (parent != null && parent.isLayoutRequested() && ViewCompat.isAttachedToWindow(child)) {
            child.post(new Runnable() {
                @Override
                public void run() {
                    startSettlingAnimation(child, state);
                }
            });
        } else {
            startSettlingAnimation(child, state);
        }
    }

    public int getState() {
        return mState;
    }

    public final void setPeekHeight(int peekHeight) {
        boolean layout = false;
        if (peekHeight == PEEK_HEIGHT_AUTO) {
            if (!mPeekHeightAuto) {
                mPeekHeightAuto = true;
                layout = true;
            }
        } else if (mPeekHeightAuto || mPeekHeight != peekHeight) {
            mPeekHeightAuto = false;
            mPeekHeight = Math.max(0, peekHeight);
            mPeekOffset = mParentHeight - peekHeight;
            layout = true;
        }
        if (layout && mState == STATE_COLLAPSED && mViewRef != null) {
            V view = mViewRef.get();
            if (view != null) {
                view.requestLayout();
            }
        }
    }

    public int getPeekHeight() {
        return mPeekHeightAuto ? PEEK_HEIGHT_AUTO : mPeekHeight;
    }

    public void setHideable(boolean hideable) {
        mHideable = hideable;
    }

    public boolean isHideable() {
        return mHideable;
    }

    public void setSkipCollapsed(boolean skipCollapsed) {
        mSkipCollapsed = skipCollapsed;
    }

    public boolean isSkipCollapsed() {
        return mSkipCollapsed;
    }

    public void setHandleHeight(int handleHeight) {
        boolean layout = false;
        if (mHandleHeight != handleHeight) {
            mHandleHeight = Math.max(0, handleHeight);
            mMaxOffset = mHandleOffset = mParentHeight - handleHeight;
            layout = true;
        }
        if (layout && mState == STATE_COLLAPSED && mViewRef != null) {
            V view = mViewRef.get();
            if (view != null) {
                view.requestLayout();
            }
        }
    }

    public void addOnStateChangedListener(OnStateChangedListener listener) {
        mStateChangedListener = listener;
    }

    public void addOnSlideListener(OnSlideListener listener) {
        mSlideListener = listener;
    }


    /**
     * Listener for monitoring state about bottom sheets.
     */
    public interface OnStateChangedListener {
        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState    The new state. This will be one of {@link #STATE_DRAGGING},
         *                    {@link #STATE_SETTLING}, {@link #STATE_EXPANDED},
         *                    {@link #STATE_COLLAPSED}, {@link #STATE_HIDDEN},
         *                    or {@link #STATE_PEEK}.
         */
        void onStateChanged(@NonNull View bottomSheet, int newState);
    }

    /**
     * Listener for monitoring slide event about bottom sheets.
     */
    public interface OnSlideListener {
        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset
         *                    increases as this bottom sheet is moving upward. From 0 to 1 the sheet
         *                    is between peek and expanded states and from -1 to 0 it is
         *                    between collapsed and peek states.
         */
        void onSlide(@NonNull View bottomSheet, float slideOffset);
    }

    /* internal */

    private View findScrollingChild(View view) {
        if (ViewCompat.isNestedScrollingEnabled(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                View scrollingChild = findScrollingChild(group.getChildAt(i));
                if (scrollingChild != null) {
                    return scrollingChild;
                }
            }
        }
        return null;
    }

    private void setStateInternal(int state) {
        if (mState == state) {
            return;
        }
        mState = state;
        View bottomSheet = mViewRef.get();
        if (bottomSheet != null && mStateChangedListener != null) {
            mStateChangedListener.onStateChanged(bottomSheet, state);
        }
    }

    private void dispatchOnSlide(int top) {
        View bottomSheet = mViewRef.get();
        if (bottomSheet != null && mSlideListener != null) {
            if (top > mPeekOffset) {
                mSlideListener.onSlide(bottomSheet, (float) (mPeekOffset - top) /
                        (mMaxOffset - mPeekOffset));
            } else {
                mSlideListener.onSlide(bottomSheet,
                        (float) (mPeekOffset - top) / ((mPeekOffset - mMinOffset)));
            }
        }
    }

    private boolean shouldHide(View child, float yVel) {
        if (mSkipCollapsed) {
            return true;
        }
        if (child.getTop() < mMaxOffset) {
            // It should not hide, but peek or collapse.
            return false;
        }
        final float newTop = child.getTop() + yVel * HIDE_FRICTION;
        return Math.abs(newTop - mMaxOffset) / (float) mPeekHeight > HIDE_THRESHOLD;
    }

    private float getYVelocity() {
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        return mVelocityTracker.getYVelocity(mActivePointerId);
    }

    private void reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void startSettlingAnimation(View child, int state) {
        int top;
        if (state == STATE_COLLAPSED) {
            top = mHandleOffset;
        } else if (state == STATE_PEEK) {
            top = mPeekOffset;
        } else if (state == STATE_EXPANDED) {
            top = mMinOffset;
        } else if (mHideable && state == STATE_HIDDEN) {
            top = mParentHeight;
        } else {
            throw new IllegalArgumentException("Illegal state argument: " + state);
        }
        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, state));
        } else {
            setStateInternal(state);
        }
    }

    private final ViewDragHelper.Callback mDragCallback = new ViewDragHelper.Callback() {

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            if (mState == STATE_DRAGGING) {
                return false;
            }
            if (mTouchingScrollingChild) {
                return false;
            }
            if (mState == STATE_EXPANDED && mActivePointerId == pointerId) {
                View scroll = mNestedScrollingChildRef.get();
                if (scroll != null && scroll.canScrollVertically(-1)) {
                    // Let the content scroll up
                    return false;
                }
            }
            return mViewRef != null && mViewRef.get() == child;
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            dispatchOnSlide(top);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING);
            }
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xVel, float yVel) {
            int top;
            int targetState;
            int childTop = releasedChild.getTop();
            if (yVel < 0) { // Moving up
                if (childTop >= mPeekOffset) {
                    top = mPeekOffset;
                    targetState = STATE_PEEK;
                } else {
                    top = mMinOffset;
                    targetState = STATE_EXPANDED;
                }
            } else if (mHideable && shouldHide(releasedChild, yVel)) {
                top = mParentHeight;
                targetState = STATE_HIDDEN;
            } else if (yVel == 0.f) {
                if (Math.abs(childTop - mMinOffset) < Math.abs(childTop - mPeekOffset)) {
                    top = mMinOffset;
                    targetState = STATE_EXPANDED;
                } else {
                    top = mPeekOffset;
                    targetState = STATE_PEEK;
                }
            } else {
                if (childTop < mPeekOffset) {
                    top = mPeekOffset;
                    targetState = STATE_PEEK;
                } else {
                    top = mHandleOffset;
                    targetState = STATE_COLLAPSED;
                }
            }
            if (mViewDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top)) {
                setStateInternal(STATE_SETTLING);
                ViewCompat.postOnAnimation(releasedChild,
                        new SettleRunnable(releasedChild, targetState));
            } else {
                setStateInternal(targetState);
            }
        }

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            int position = MathUtils.clamp(top, mMinOffset, mHideable ? mParentHeight : mMaxOffset);
            return position;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return child.getLeft();
        }

        @Override
        public int getViewVerticalDragRange(@NonNull View child) {
            int range = mHideable ? mParentHeight - mMinOffset : mMaxOffset - mMinOffset;
            return range;
        }
    };

    private class SettleRunnable implements Runnable {

        private final View mView;

        private final int mTargetState;

        SettleRunnable(View view, int targetState) {
            mView = view;
            mTargetState = targetState;
        }

        @Override
        public void run() {
            if (mViewDragHelper != null && mViewDragHelper.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this);
            } else {
                setStateInternal(mTargetState);
            }
        }
    }

    private static class SavedState extends AbsSavedState {
        final int state;

        SavedState(Parcelable superState, int state) {
            super(superState);
            this.state = state;
        }

        SavedState(Parcel source) {
            super(source);
            state = source.readInt();
        }

        @TargetApi(Build.VERSION_CODES.N)
        SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            state = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(state);
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel source, ClassLoader loader) {
                return new SavedState(source, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <V extends View> ExBottomSheetBehavior<V> from(V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params)
                .getBehavior();
        if (!(behavior instanceof ExBottomSheetBehavior)) {
            throw new IllegalArgumentException(
                    "The view is not associated with ExBottomSheetBehavior");
        }
        return (ExBottomSheetBehavior<V>) behavior;
    }

}
