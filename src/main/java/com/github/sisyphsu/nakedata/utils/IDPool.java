package com.github.sisyphsu.nakedata.utils;

/**
 * allocate [0, max] id
 *
 * @author sulin
 * @since 2019-04-29 17:37:20
 */
public class IDPool {

    /**
     * The next incremental id, if no reuseIds, it should be used at next time.
     */
    private int   nextId;
    /**
     * The real count of reuseIds.
     */
    private int   reuseCount;
    /**
     * The id was released.
     */
    private int[] reuseIds;

    /**
     * Acquire an unique and incremental id, if have released id, use it first.
     *
     * @return Unique and incremental id
     */
    public int acquire() {
        if (reuseCount == 0) {
            return nextId++;
        }
        return this.reuseIds[--reuseCount];
    }

    /**
     * Release the specified id, It will be used in high priority.
     *
     * @param id ID was released
     */
    public void release(int id) {
        if (reuseIds == null) {
            // init
            reuseIds = new int[64];
        } else if (reuseIds.length >= reuseCount) {
            // expansion
            int[] tmp = this.reuseIds;
            reuseIds = new int[reuseIds.length * 2];
            System.arraycopy(tmp, 0, reuseIds, 0, tmp.length);
        }
        this.reuseIds[this.reuseCount] = id;
        ArrayUtils.descSort(this.reuseIds, 0, this.reuseCount);

        this.reuseCount++;
    }

}