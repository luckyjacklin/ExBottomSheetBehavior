package me.jack.bottomsheet;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import me.jack.behavior.ExBottomSheetBehavior;

public class MainActivity extends AppCompatActivity {

    private ExBottomSheetBehavior mBehavior;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBehavior = ExBottomSheetBehavior.from(findViewById(R.id.layout_bottom_sheet));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.state_expanded:
                mBehavior.setState(ExBottomSheetBehavior.STATE_EXPANDED);
                break;
            case R.id.state_peek:
                mBehavior.setState(ExBottomSheetBehavior.STATE_PEEK);
                break;
            case R.id.state_collapsed:
                mBehavior.setState(ExBottomSheetBehavior.STATE_COLLAPSED);
                break;
            case R.id.state_hidden:
                mBehavior.setHideable(true);
                mBehavior.setState(ExBottomSheetBehavior.STATE_HIDDEN);
                break;
            case R.id.skip_collapsed:
                mBehavior.setSkipCollapsed(true);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}
