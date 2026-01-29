#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <vector>
#include <algorithm>
#include <cfloat>

#define LOG_TAG "RouteSolver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const int MAX_CP = 17;
static const int MAX_SLOTS = 15;
static const int ALL_NODES = 19; // 17 intermediates + Start(17) + Finish(18)
static const int START_IDX = 17;
static const int FINISH_IDX = 18;
static const float INF_TIME = 1e9f;

struct SolverInput {
    int n_checkpoints;          // 17
    int n_slots;                // 15
    float travel_time[ALL_NODES][ALL_NODES];
    bool open_at[MAX_CP][MAX_SLOTS];   // intermediate CP openings
    bool finish_open[MAX_SLOTS];       // Finish openings
    int slot_starts[MAX_SLOTS];        // slot start times in minutes
    float speed;
    int dwell;                  // 7
    float naismith;             // 10.0
    int start_time;             // 600
    int end_time;               // 1020
};

struct SolverResult {
    int count;                  // checkpoints visited
    int route[MAX_CP];          // CP indices in order
    int route_length;
    float finish_time;          // in minutes from midnight
};

// Count set bits (popcount)
static inline int popcount(int x) {
    int c = 0;
    while (x) { c += x & 1; x >>= 1; }
    return c;
}

// Convert arrival time (minutes from midnight) to slot index.
// Matches Python: minute-of-hour must be *strictly greater than* 30 to advance to :30 slot.
static int arrival_to_slot_index(float arrival_minutes, const SolverInput* input) {
    if (arrival_minutes < (float)input->slot_starts[0]) {
        return -1;
    }
    int whole = (int)arrival_minutes;
    int h = whole / 60;
    int m = whole % 60;
    int slot_time = h * 60 + (m > 30 ? 30 : 0);
    if (slot_time > input->slot_starts[input->n_slots - 1]) {
        return input->n_slots - 1;
    }
    for (int s = 0; s < input->n_slots; s++) {
        if (input->slot_starts[s] == slot_time) {
            return s;
        }
    }
    return -1;
}

// Find earliest time >= arrival_minutes when checkpoint cp_idx is open.
// Returns -1.0f if no future slot is open.
static float find_next_open_time(int cp_idx, float arrival_minutes, const SolverInput* input) {
    int slot = arrival_to_slot_index(arrival_minutes, input);
    if (slot < 0) slot = 0;
    for (int s = slot; s < input->n_slots; s++) {
        if (input->open_at[cp_idx][s]) {
            float t = arrival_minutes > (float)input->slot_starts[s]
                      ? arrival_minutes : (float)input->slot_starts[s];
            return t;
        }
    }
    return -1.0f;
}

// Check if we can reach Finish from current_idx within an open Finish window.
static bool can_reach_finish(float current_time, int current_idx, const SolverInput* input) {
    float t_to_finish = input->travel_time[current_idx][FINISH_IDX];
    float finish_arrival = current_time + t_to_finish;
    if (finish_arrival > (float)input->end_time) {
        return false;
    }
    int slot = arrival_to_slot_index(finish_arrival, input);
    if (slot < 0 || slot >= input->n_slots) {
        return false;
    }
    for (int s = slot; s < input->n_slots; s++) {
        if (input->finish_open[s]) {
            float wait_until = finish_arrival > (float)input->slot_starts[s]
                               ? finish_arrival : (float)input->slot_starts[s];
            if (wait_until <= (float)input->end_time) {
                return true;
            }
        }
    }
    return false;
}

// Main bitmask DP solver.
static void solve(SolverInput* input, SolverResult* result) {
    int N = input->n_checkpoints;
    int total_states = (1 << N) * N;

    LOGI("Solving: N=%d, speed=%.2f, states=%d", N, input->speed, total_states);

    // Allocate DP arrays
    std::vector<float> dp(total_states, INF_TIME);
    // parent encoding: -1 = no parent, otherwise packed as (prev_mask << 5) | prev_pos
    // But mask can be up to 2^17, so we need 17+5=22 bits. Use int32.
    // Pack as: prev_pos in low 5 bits, prev_mask in upper bits. -1 = from Start.
    std::vector<int> parent(total_states, -2); // -2 = unvisited, -1 = from Start

    auto idx = [&](int mask, int pos) -> int {
        return mask * N + pos;
    };

    float depart_start = (float)input->start_time;

    // Initialize: Start -> each intermediate CP
    for (int j = 0; j < N; j++) {
        float arr = depart_start + input->travel_time[START_IDX][j];
        float open_time = find_next_open_time(j, arr, input);
        if (open_time < 0.0f) continue;
        float depart_j = open_time + (float)input->dwell;
        if (depart_j > (float)input->end_time) continue;
        if (!can_reach_finish(depart_j, j, input)) continue;

        int mask = 1 << j;
        int si = idx(mask, j);
        if (depart_j < dp[si]) {
            dp[si] = depart_j;
            parent[si] = -1; // came from Start
        }
    }

    // Group masks by popcount and process in order
    // We process popcount 1..N
    // For efficiency, collect masks that have valid dp entries
    std::vector<std::vector<int>> masks_by_pc(N + 1);
    for (int j = 0; j < N; j++) {
        int mask = 1 << j;
        int si = idx(mask, j);
        if (dp[si] < INF_TIME) {
            masks_by_pc[1].push_back(mask);
        }
    }
    // Remove duplicates in masks_by_pc[1]
    std::sort(masks_by_pc[1].begin(), masks_by_pc[1].end());
    masks_by_pc[1].erase(std::unique(masks_by_pc[1].begin(), masks_by_pc[1].end()),
                         masks_by_pc[1].end());

    // Main DP loop
    for (int pc = 1; pc < N; pc++) {
        for (int mask : masks_by_pc[pc]) {
            for (int i = 0; i < N; i++) {
                if (!(mask & (1 << i))) continue;
                int si = idx(mask, i);
                if (dp[si] >= INF_TIME) continue;
                float depart_i = dp[si];

                // Try extending to each unvisited CP
                for (int j = 0; j < N; j++) {
                    if (mask & (1 << j)) continue;
                    float arr_j = depart_i + input->travel_time[i][j];
                    if (arr_j > (float)input->end_time) continue;
                    float open_time = find_next_open_time(j, arr_j, input);
                    if (open_time < 0.0f) continue;
                    float depart_j = open_time + (float)input->dwell;
                    if (depart_j > (float)input->end_time) continue;
                    if (!can_reach_finish(depart_j, j, input)) continue;

                    int new_mask = mask | (1 << j);
                    int new_si = idx(new_mask, j);
                    if (depart_j < dp[new_si]) {
                        dp[new_si] = depart_j;
                        // Pack parent: (mask << 5) | i
                        parent[new_si] = (mask << 5) | i;

                        // Register new_mask in next popcount bucket
                        int npc = pc + 1;
                        // We'll deduplicate later
                        masks_by_pc[npc].push_back(new_mask);
                    }
                }
            }
        }
        // Deduplicate next popcount bucket
        if (pc + 1 <= N) {
            std::sort(masks_by_pc[pc + 1].begin(), masks_by_pc[pc + 1].end());
            masks_by_pc[pc + 1].erase(
                std::unique(masks_by_pc[pc + 1].begin(), masks_by_pc[pc + 1].end()),
                masks_by_pc[pc + 1].end());
        }
    }

    // Find the best result
    int best_count = -1;
    float best_finish_time = INF_TIME;
    int best_mask = -1;
    int best_last = -1;

    for (int mask = 1; mask < (1 << N); mask++) {
        int count = popcount(mask);
        for (int i = 0; i < N; i++) {
            int si = idx(mask, i);
            if (dp[si] >= INF_TIME) continue;
            // Can we reach Finish?
            float finish_arr = dp[si] + input->travel_time[i][FINISH_IDX];
            if (finish_arr > (float)input->end_time) continue;

            int fslot = arrival_to_slot_index(finish_arr, input);
            if (fslot < 0) continue;

            float actual_finish = -1.0f;
            for (int s = fslot; s < input->n_slots; s++) {
                if (input->finish_open[s]) {
                    actual_finish = finish_arr > (float)input->slot_starts[s]
                                    ? finish_arr : (float)input->slot_starts[s];
                    break;
                }
            }
            if (actual_finish < 0.0f || actual_finish > (float)input->end_time) continue;

            if ((count > best_count) ||
                (count == best_count && actual_finish < best_finish_time)) {
                best_count = count;
                best_finish_time = actual_finish;
                best_mask = mask;
                best_last = i;
            }
        }
    }

    if (best_count < 0) {
        result->count = 0;
        result->route_length = 0;
        result->finish_time = 0.0f;
        LOGI("No feasible route found");
        return;
    }

    // Reconstruct route
    int route_buf[MAX_CP];
    int route_len = 0;
    int cur_mask = best_mask;
    int cur_pos = best_last;

    while (true) {
        route_buf[route_len++] = cur_pos;
        int si = idx(cur_mask, cur_pos);
        int p = parent[si];
        if (p == -1) {
            // Came from Start
            break;
        }
        if (p == -2) {
            // Should not happen
            LOGE("Parent chain broken at mask=%d pos=%d", cur_mask, cur_pos);
            break;
        }
        int prev_pos = p & 0x1F;
        int prev_mask = p >> 5;
        cur_mask = prev_mask;
        cur_pos = prev_pos;
    }

    // Reverse the route
    result->count = best_count;
    result->route_length = route_len;
    result->finish_time = best_finish_time;
    for (int i = 0; i < route_len; i++) {
        result->route[i] = route_buf[route_len - 1 - i];
    }

    LOGI("Solved: %d checkpoints, finish=%.1f", best_count, best_finish_time);
}


// ── JNI Bridge ──────────────────────────────────────────────────────

extern "C" JNIEXPORT jintArray JNICALL
Java_com_scout_routeplanner_solver_NativeSolver_solveNative(
    JNIEnv* env, jobject /* thiz */,
    jfloatArray travelTimeMatrix,
    jbooleanArray openingsFlat,
    jbooleanArray finishOpenings,
    jintArray slotStarts,
    jfloat speed, jint dwell, jfloat naismith,
    jint startTime, jint endTime,
    jint nCheckpoints, jint nSlots)
{
    SolverInput input;
    memset(&input, 0, sizeof(input));
    input.n_checkpoints = nCheckpoints;
    input.n_slots = nSlots;
    input.speed = speed;
    input.dwell = dwell;
    input.naismith = naismith;
    input.start_time = startTime;
    input.end_time = endTime;

    // Copy travel time matrix (ALL_NODES x ALL_NODES flattened)
    jfloat* ttFlat = env->GetFloatArrayElements(travelTimeMatrix, nullptr);
    for (int i = 0; i < ALL_NODES; i++) {
        for (int j = 0; j < ALL_NODES; j++) {
            input.travel_time[i][j] = ttFlat[i * ALL_NODES + j];
        }
    }
    env->ReleaseFloatArrayElements(travelTimeMatrix, ttFlat, 0);

    // Copy openings (N x nSlots flattened)
    jboolean* openFlat = env->GetBooleanArrayElements(openingsFlat, nullptr);
    for (int i = 0; i < nCheckpoints; i++) {
        for (int s = 0; s < nSlots; s++) {
            input.open_at[i][s] = openFlat[i * nSlots + s] != 0;
        }
    }
    env->ReleaseBooleanArrayElements(openingsFlat, openFlat, 0);

    // Copy finish openings
    jboolean* finOpen = env->GetBooleanArrayElements(finishOpenings, nullptr);
    for (int s = 0; s < nSlots; s++) {
        input.finish_open[s] = finOpen[s] != 0;
    }
    env->ReleaseBooleanArrayElements(finishOpenings, finOpen, 0);

    // Copy slot starts
    jint* slotStartsArr = env->GetIntArrayElements(slotStarts, nullptr);
    for (int s = 0; s < nSlots; s++) {
        input.slot_starts[s] = slotStartsArr[s];
    }
    env->ReleaseIntArrayElements(slotStarts, slotStartsArr, 0);

    // Solve
    SolverResult result;
    memset(&result, 0, sizeof(result));
    solve(&input, &result);

    // Return as int array: [count, route_length, finish_time_x100, route[0], route[1], ...]
    int outputSize = 3 + result.route_length;
    jintArray output = env->NewIntArray(outputSize);
    std::vector<jint> outBuf(outputSize);
    outBuf[0] = result.count;
    outBuf[1] = result.route_length;
    outBuf[2] = (int)(result.finish_time * 100.0f); // encode as centiseconds
    for (int i = 0; i < result.route_length; i++) {
        outBuf[3 + i] = result.route[i];
    }
    env->SetIntArrayRegion(output, 0, outputSize, outBuf.data());

    return output;
}
