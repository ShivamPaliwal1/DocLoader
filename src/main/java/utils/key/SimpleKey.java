package utils.key;

import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

import utils.docgen.WorkLoadSettings;

public class SimpleKey {
    public WorkLoadSettings ws;
    String padding = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            + "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    String alphabet = "";
    public int key_counter = 0;

    private static int total_vbs = 1024;

    public SimpleKey() {
        super();
    }

    public void set_total_vbs(int num_vbs) {
        this.total_vbs = num_vbs;
    }

    public static boolean contains(int[] array, int target) {
        for (int num : array)
            if (num == target)
                return true;
        return false;
    }

    public SimpleKey(WorkLoadSettings ws) {
        super();
        this.ws = ws;
    }

    public int get_vbucket_for_key(String key) {
        CRC32 crc = new CRC32();
        crc.update(key.getBytes());
        return ((((int)crc.getValue() >> 16) & 0x7fff) & (total_vbs-1));
    }

    public String next(long doc_index) {
        int counterSize = Long.toString(Math.abs(doc_index)).length();
        int padd = this.ws.keySize - this.ws.keyPrefix.length() - counterSize;
        return this.ws.keyPrefix + this.padding.substring(0, padd) + Math.abs(doc_index);
    }

    public Map<Long, String> generate_keys_for_target_vbs(Long doc_index, Long num_keys, int[] target_vbs) {
        // Doc index 'i' is the i'th key of the whole keyspace that lands in
        // target_vbs, counted from a fixed origin. Scanning from 'doc_index'
        // instead made the key depend on where the window started, so two
        // index ranges that do not overlap still returned overlapping keys.
        // Cost is therefore proportional to the window's end index, not its
        // width: ~1.4s for 42 of 128 vBuckets at index 5_000_000, but minutes
        // for a single vBucket of 1024. No caller combines a sparse target
        // set with a high start index.
        Map<Long, String> generated_keys = new HashMap<Long, String>();

        // An empty or reversed range asks for nothing. Checked before
        // 'last_index' is computed off it, so the scan is not entered just to
        // return this same empty map (~1s at a doc_index of 5_000_000).
        if (num_keys <= 0) {
            return generated_keys;
        }

        Integer vb_of_key;
        long last_index = doc_index + num_keys;
        long matched = 0;
        long key_index = 0;
        String key;

        while(matched < last_index) {
            key = this.next(key_index);
            key_index ++;

            vb_of_key = this.get_vbucket_for_key(key);
            for (int vb_num : target_vbs) {
                if (vb_num == vb_of_key) {
                    if (matched >= doc_index) {
                        generated_keys.put(matched, key);
                    }
                    matched ++;
                    break;
                }
            }
        }
        return generated_keys;
    }
}
