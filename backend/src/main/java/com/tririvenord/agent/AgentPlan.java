package com.tririvenord.agent;

import java.time.Instant;
import java.util.List;

/**
 * A proposal, not an action.
 *
 * @param id          opaque handle the administrator posts back to approve
 * @param adminUserId who asked; execution checks this, so one administrator
 *                    cannot execute a plan another one was shown
 * @param municipalityId which city the plan was drawn up against. Stored with
 *                    the plan rather than supplied again at approval, for the
 *                    same reason adminUserId is: a value re-sent at execution
 *                    is a value that can differ from the one the plan was
 *                    reviewed under. The administrator approves "move
 *                    Thursday's pickup in Blainville", and nothing between
 *                    the screen and the write should be able to turn that
 *                    into the same edit somewhere else.
 * @param instruction what was originally typed, kept so the confirmation screen
 *                    can show request and proposal side by side
 * @param narrative   the model's own explanation of what it intends and why
 * @param steps       the writes, in the order they will run
 */
public record AgentPlan(
        String id,
        Long adminUserId,
        long municipalityId,
        String instruction,
        String narrative,
        List<PlannedStep> steps,
        Instant createdAt
) {
}
