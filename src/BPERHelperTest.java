import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BPERHelperTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void stringToTimestamp() {
        System.out.println(BPERHelper.getInstance().StringToTimestamp("2020-07-10T11:16:22.4+02:00"));
        System.out.println(BPERHelper.getInstance().StringToTimestamp("2020-07-08T11:32:25.54+02:00"));
        System.out.println(BPERHelper.getInstance().StringToTimestamp("2021-03-01T16:53:11.273+01:00"));
        System.out.println(BPERHelper.getInstance().StringToTimestamp("2021-03-01T16:52:27.827+01:00"));
        System.out.println(BPERHelper.getInstance().StringToTimestamp("2021-03-01T10:43:48.9+01:00"));
    }
}