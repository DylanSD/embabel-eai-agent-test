package com.embabel.template.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.core.types.Timestamped;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

abstract class OmPersonas {
    static final RoleGoalBackstory COACH = RoleGoalBackstory
            .withRole("Odyssey of the Mind Coach")
            .andGoal("Design a complete 20-week season plan that prepares a team for OM competition while avoiding Outside Assistance")
            .andBackstory("Experienced OM coach and spontaneous trainer; emphasizes divergent thinking, teamwork, safety, and student ownership.");

    static final Persona REVIEWER = Persona.create(
            "OM Season Plan Reviewer",
            "Veteran OM Judge & Coach",
            "Supportive, precise, and rules-aware",
            "Ensure the season plan is feasible, fun, and aligned to OM Program Guide rules (no Outside Assistance; include Style and Spontaneous)."
    );
}

record WeekLesson(
        int weekNumber,
        String goals,
        String longTermFocus,
        String styleFocus,
        String spontaneousType,
        List<String> agenda,
        List<String> materials,
        List<String> safetyNotes,
        List<String> checkpoints,
        List<String> homework,
        String outsideAssistanceGuardrails
) {}

record SeasonPlan(
        String teamLevel,
        String longTermProblemType,
        String theme,
        List<WeekLesson> lessons
) implements HasContent, Timestamped {

    @Override
    @NonNull
    public Instant getTimestamp() { return Instant.now(); }

    @Override
    @NonNull
    public String getContent() {
        var date = getTimestamp().atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
        return ("# OM 20-Week Season Plan\n\n" +
                "**Team level:** " + teamLevel + "\n" +
                "**Long-Term problem type:** " + longTermProblemType + "\n" +
                "**Theme:** " + theme + "\n" +
                "**Generated:** " + date + "\n\n" +
                "## Lessons (20 weeks)\n" +
                lessons.stream()
                        .map(w -> "### Week " + w.weekNumber() + "\n" +
                                "**Goals:** " + w.goals() + "\n" +
                                "**Long-Term Focus:** " + w.longTermFocus() + "\n" +
                                "**Style Focus:** " + w.styleFocus() + "\n" +
                                "**Spontaneous:** " + w.spontaneousType() + "\n" +
                                "**Agenda:**\n- " + String.join("\n- ", w.agenda()) + "\n" +
                                "**Materials:**\n- " + String.join("\n- ", w.materials()) + "\n" +
                                "**Safety Notes:**\n- " + String.join("\n- ", w.safetyNotes()) + "\n" +
                                "**Checkpoints:**\n- " + String.join("\n- ", w.checkpoints()) + "\n" +
                                "**Homework:**\n- " + String.join("\n- ", w.homework()) + "\n" +
                                "**Outside Assistance Guardrails:** " + w.outsideAssistanceGuardrails() + "\n")
                        .reduce("", (a,b) -> a + "\n" + b));
    }
}

record ReviewedSeasonPlan(
        SeasonPlan plan,
        String review,
        Persona reviewer
) implements HasContent, Timestamped {

    @Override
    @NonNull
    public Instant getTimestamp() { return Instant.now(); }

    @Override
    @NonNull
    public String getContent() {
        return String.format("""
                        # Odyssey of the Mind Season Plan
                        %s

                        ---
                        # Reviewer Notes
                        %s

                        # Reviewer
                        %s, %s
                        """.trim(),
                plan.getContent(),
                review,
                reviewer.getName(),
                getTimestamp().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"))
        );
    }
}

@Agent(description = "Create a 20-week Odyssey of the Mind coaching plan and have it reviewed")
@Profile("!test")
class OmCoachAgent {

    private final double coachTemperature;
    private final int maxWordsPerWeek;
    private final int reviewWordLimit;

    OmCoachAgent(
            @Value("${coachTemperature:0.5}") double coachTemperature,
            @Value("${maxWordsPerWeek:180}") int maxWordsPerWeek,
            @Value("${reviewWordLimit:400}") int reviewWordLimit
    ) {
        this.coachTemperature = coachTemperature;
        this.maxWordsPerWeek = maxWordsPerWeek;
        this.reviewWordLimit = reviewWordLimit;
    }

    @AchievesGoal(
            description = "A complete 20-week OM season plan has been crafted and reviewed",
            export = @Export(remote = true, name = "generateAndReviewOmSeason"))
    @Action
    ReviewedSeasonPlan generateAndReview(UserInput userInput, OperationContext context) {
        var plan = craftSeasonPlan(userInput, context);
        return reviewSeason(userInput, plan, context);
    }

    @Action
    SeasonPlan craftSeasonPlan(UserInput userInput, OperationContext context) {
        String prompt = ("""
                You are an Odyssey of the Mind (OM) coach. Odyssey of the Mind is a creative problem-solving
                competition for students and community group members of all ages and learning levels using a combination
                of engineering, dramatic performance and lateral thinking. Teams of students select a problem, create a
                solution, then present their solution in a competition against other teams in the same problem and division.
                There are many nuances of the program that are explained further in the guide, but here are some of the basics
                of participation.

                Design a COMPLETE 20-week season plan that prepares a team for competition while following the OM Program Guide.
                Your plan must include Long-Term work, Style development, and Spontaneous practice EVERY week.

                CRITICAL OM CONSTRAINTS TO HONOR (build into the plan text):
                - Outside Assistance is prohibited: only team members may design/build/present the solution.
                  Coaches/parents may only teach general skills, provide safety oversight, ask open questions,
                  and schedule practices. Do NOT include any step where an adult makes design decisions.
                - Spontaneous must rotate types (verbal, hands-on, hybrid) and include example prompts or setups.
                - Style is scored separately; include recurring style exploration and 2-3 candidate Style items.
                - Emphasize brainstorming, convergence, testing, iteration, documentation, and safety.
                - Keep weekly write-ups concise (<= %d words per week).
                - for the spontaneous problem ideas, you need to provide specific instructions and materials list
                - The plan must be 20 weeks long
                - Include a good materials list that is somewhat efficient across all 20 weeks.

                INPUT FROM USER (preferences, team info, constraints; may be empty):
                ---\n%s\n---

                REQUIRED OUTPUT FORMAT (STRICT JSON ONLY — no prose):
                {
                  "teamLevel": "Division I | Division II | Division III | mixed | unknown",
                  "longTermProblemType": "vehicle | technical | classics | structure | performance | TBD",
                  "theme": "short motivational theme for the season",
                  "lessons": [
                    {
                      "weekNumber": 1,
                      "goals": "...",
                      "longTermFocus": "...",
                      "styleFocus": "...",
                      "spontaneousType": "verbal | hands-on | hybrid — include 1 quick sample prompt",
                      "agenda": ["mm:ss activity", "..."],
                      "materials": ["generic supplies only (no design decisions)", "..."],
                      "safetyNotes": ["..."],
                      "checkpoints": ["clear definitions of done"],
                      "homework": ["thinking logs only; no building"],
                      "outsideAssistanceGuardrails": "explicit guardrail text"
                    },
                    { "weekNumber": 2, ... },
                    { "weekNumber": 3, ... }
                    // ... up to week 20
                  ]
                }
                """
        ).formatted(maxWordsPerWeek, userInput.getContent()).trim();

        return context.ai()
                .withLlm(LlmOptions.withAutoLlm().withTemperature(coachTemperature))
                .withPromptContributor(OmPersonas.COACH)
                .createObject(prompt, SeasonPlan.class);
    }

    @Action
    ReviewedSeasonPlan reviewSeason(UserInput userInput, SeasonPlan plan, OperationContext context) {
        String review = context.ai()
                .withAutoLlm()
                .withPromptContributor(OmPersonas.REVIEWER)
                .generateText(("""
                        You are reviewing a 20-week Odyssey of the Mind season plan. In %d words or less,
                        evaluate the plan on:
                        1) Outside Assistance compliance (coach/parent roles stay within rules),
                        2) Coverage & balance across Long-Term, Style, and Spontaneous (verbal/hands-on/hybrid rotation),
                        3) Feasibility (materials, time, safety), clarity (checkpoints), and student ownership,
                        4) Suggestions to strengthen Style items, documentation, and tournament readiness.
                        5) The plan must be 20 weeks long
                        6) Does it include a good materials list that is somewhat efficient across all 20 weeks?

                        Provide actionable bullet-point feedback. Reference specific weeks when helpful.

                        # USER INPUT (context)
                        %s

                        # PLAN (verbatim)
                        %s
                        """
                ).formatted(reviewWordLimit, userInput.getContent(), plan.getContent()).trim());

        return new ReviewedSeasonPlan(plan, review, OmPersonas.REVIEWER);
    }

    @Action
    WeekLesson regenerateWeek(UserInput userInput, SeasonPlan currentPlan, int weekNumber, OperationContext context) {
        String prompt = ("""
                Regenerate Week %d of an Odyssey of the Mind season plan using the same constraints as before.
                Keep the plan consistent with the current season theme and problem type.
                Return STRICT JSON for the WeekLesson record only.

                USER INPUT (for context):
                %s

                CURRENT PLAN (for context):
                %s
                """
        ).formatted(weekNumber, userInput.getContent(), currentPlan.getContent());

        return context.ai()
                .withLlm(LlmOptions.withAutoLlm().withTemperature(coachTemperature))
                .withPromptContributor(OmPersonas.COACH)
                .createObject(prompt, WeekLesson.class);
    }
}
