---
description: Required analysis-before-coding workflow for every user request.
globs: "**/*"
---

# Request Handling Workflow

1. **Investigate before coding.** Upon receiving any new requirement, locate and read the relevant files, components, and data flows. Do not start editing until you understand the existing implementation.
2. **Share feasibility and impact.** Summarize what parts of the codebase are involved, why the change is (or is not) feasible, and outline possible approaches. This written analysis must happen before touching the code.
3. **Wait for confirmation.** Only begin modifying files after the requester explicitly agrees with the proposed plan. If multiple options exist, reach consensus first.
4. **Surface uncertainties immediately.** If any instruction is unclear or information is missing, describe the confusion explicitly and list the extra details you need instead of guessing.
5. **Keep execution transparent.** Once implementation starts, continue to explain key steps, assumptions, and validation methods so reviewers can follow the reasoning path.

These steps are mandatory for every feature, UI tweak, or bug fix. Skipping any of them is considered a process violation.

