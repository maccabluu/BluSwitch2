// SPDX-License-Identifier: GPL-3.0-or-later
package com.blustudio.bluswitch2.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Original demo game. Android draws and forwards input; guest AArch64 code
 * makes all movement and win decisions. Not Nintendo Switch 2 game support. */
final class MiniGameDialog {
    private MiniGameDialog() {}

    private static int dp(Activity activity,int units) {
        return Math.round(units*activity.getResources().getDisplayMetrics().density);
    }
    private static GradientDrawable tile(int color) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(15f);
        return d;
    }
    private static TextView label(Activity a,String text,int size) {
        TextView v=new TextView(a);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        return v;
    }
    static void show(Activity activity) {
        final int[] state={0,0,0,0};
        final LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity,14),dp(activity,10),dp(activity,14),dp(activity,10));
        content.setBackgroundColor(Color.rgb(8,22,45));

        TextView title=label(activity,"BLU TREASURE HUNT",19);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        content.addView(title);
        final TextView message=label(activity,"Reach the star using the direction buttons",14);
        content.addView(message);
        GridLayout board=new GridLayout(activity);
        board.setColumnCount(3);
        board.setRowCount(3);
        board.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        board.setUseDefaultMargins(true);
        final TextView[] cells=new TextView[9];
        for(int i=0;i<9;++i) {
            TextView cell=label(activity,"",25);
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(
                    GridLayout.spec(i/3),GridLayout.spec(i%3));
            p.width=dp(activity,76);
            p.height=dp(activity,58);
            p.setMargins(dp(activity,3),dp(activity,3),dp(activity,3),dp(activity,3));
            board.addView(cell,p);
            cells[i]=cell;
        }
        LinearLayout.LayoutParams boardParams=new LinearLayout.LayoutParams(-2,-2);
        boardParams.gravity=Gravity.CENTER;
        boardParams.topMargin=dp(activity,12);
        content.addView(board,boardParams);
        final TextView score=label(activity,"Moves: 0",13);
        content.addView(score);

        LinearLayout controls=new LinearLayout(activity);
        controls.setGravity(Gravity.CENTER);
        content.addView(controls);
        final Button[] directions=new Button[4];
        final int[] commands={3,1,2,4};
        final String[] names={"LEFT","UP","DOWN","RIGHT"};
        final Runnable render=()->{
            for(int i=0;i<9;++i) {
                final boolean player=(i%3==state[0]&&i/3==state[1]);
                final boolean star=i==8;
                cells[i].setText(player?(star?"★":"●"):(star?"★":""));
                cells[i].setTextColor(player?Color.WHITE:Color.rgb(255,210,77));
                cells[i].setBackground(tile(player?Color.rgb(35,122,225):Color.rgb(20,50,91)));
            }
            score.setText("Moves: "+state[2]);
            message.setText(state[3]==1?"You found the treasure!":"Find the star at the bottom right");
            for(Button b:directions) if(b!=null)b.setEnabled(state[3]==0);
        };
        for(int i=0;i<4;++i) {
            final int move=commands[i];
            Button b=new Button(activity);
            b.setText(names[i]);
            b.setAllCaps(false);
            directions[i]=b;
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(activity,60),1);
            p.setMargins(dp(activity,2),dp(activity,4),dp(activity,2),0);
            controls.addView(b,p);
            b.setOnClickListener(view->{
                try {
                    int[] next=NativeCore.stepMiniGame(state[0],state[1],state[2],state[3],move);
                    if(next==null||next.length!=6||next[5]!=1) {
                        message.setText("Guest game execution failed.");
                        return;
                    }
                    System.arraycopy(next,0,state,0,4);
                    render.run();
                } catch(RuntimeException|LinkageError ex) {
                    message.setText("Native game error: "+ex.getClass().getSimpleName());
                }
            });
        }
        Button again=new Button(activity);
        again.setText("Play again");
        again.setAllCaps(false);
        content.addView(again);
        again.setOnClickListener(v->{
            for(int i=0;i<state.length;++i)state[i]=0;
            render.run();
        });
        render.run();
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setView(content)
                .setNegativeButton("Close",null)
                .create();
        dialog.setOnKeyListener((d,keyCode,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            int index=-1;
            switch(keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: index=0;break;
                case KeyEvent.KEYCODE_DPAD_UP: index=1;break;
                case KeyEvent.KEYCODE_DPAD_DOWN: index=2;break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: index=3;break;
                case KeyEvent.KEYCODE_BUTTON_START:
                    again.performClick();return true;
                default:return false;
            }
            if(index>=0&&directions[index].isEnabled())directions[index].performClick();
            return true;
        });
        dialog.show();
    }
}
