---
description: Core stability and implementation principles for WooAutoPrinter. Always follow these before making changes.
globs: "**/*.{kt,xml,gradle}"
---

# Project Implementation & Stability Guidelines

## 0. Meta-Rule: Rule Maintenance
- **English Only:** All new rules added to this directory or file MUST be written in **English**. This ensures optimal reasoning performance for AI models.
- **Consistency:** Maintain the existing structure and tone when adding new guidelines.

## 1. Prime Directive: Stability First
**Do NOT break existing functionality.**
- Every change must maintain backward compatibility and preserve existing user behaviors.
- **Zero Regression:** The application must remain buildable and runnable at every step. Do not leave the codebase in a broken state between tool calls.

## 2. Implementation Strategy (The "Check First" Rule)
Before writing any new code, you MUST follow this sequence:
1.  **Search Existing Code:** Look for similar implementations, helper functions, or patterns already in the codebase.
2.  **Reuse & Extend:** If a similar implementation exists, prefer extending it via small, incremental changes rather than rewriting it from scratch.
3.  **Avoid Unnecessary Refactoring:** Do not refactor working code unless strictly necessary for the current task. "It works" > "It looks perfect".

## 3. Handling Major Changes
If a requirement forces a fundamental change to existing logic:
- **Parallel Implementation:** Create a new path (e.g., `NewPrinterManager`) alongside the old one rather than modifying the old one destructively.
- **Feature Flags:** Use configuration flags to switch between old and new logic safely.
- **Rollback Plan:** Ensure it is easy to revert to the previous state if the new logic fails.

## 4. Android/Kotlin Specific Guidelines
- **Coroutines:** Use `Dispatchers.IO` for all database/network/printer operations. Never block the Main thread.
- **Exception Handling:** Always wrap Bluetooth/Printer IO operations in `try-catch` blocks. Printer connections are unstable by nature; assume they will fail.
- **Resources:** Always close streams and release connections (BluetoothSockets) in `finally` blocks or use `use { }`.
- **Testing (Industrial Standard):**
    - Prefer adding **Unit Tests** for pure logic (e.g., protocol parsing, command generation) to ensure correctness without hardware.
    - Treat testability as a code quality metric. If logic is hard to test, it probably needs refactoring.

## 6. Documentation & Knowledge Management
- **Project Map Maintenance:** Whenever you add a new file, rename a class, or refactor a directory, you MUST update `PROJECT_STRUCTURE.md` immediately.
- **Detail Level:** Do not just list files; describe their **responsibility** and **key functions**. Future AI sessions rely on this map to navigate the codebase efficiently.

