package com.krepchenko.fwatchface

import com.krepchenko.fwatchface.utils.TimeToTextUtil
import org.junit.Test

/**
 * Created by ann on 6/5/17.
 */
class ShowTextTest{
    @Test
    fun _24HourTest(){
        for (h in 0..24){
            for(m in 0..60){
                System.out.println(TimeToTextUtil.getFirstLine())
                if (m>0) {
                    System.out.println(TimeToTextUtil.getSecondLine(m))
                    if (m in 21..29 || m in 31..39) {
                        System.out.println(TimeToTextUtil.getThirdLine(m))
                    }
                }
                System.out.println(TimeToTextUtil.getFourthLine(m,h))
                System.out.println()

            }
        }
    }
}