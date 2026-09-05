# Skill Registry

**Delegator use only.** Any agent that launches sub-agents reads this registry to resolve compact rules, then injects them directly into sub-agent prompts. Sub-agents do NOT read this registry or individual SKILL.md files.

See `_shared/skill-resolver.md` for the full resolution protocol.

## User Skills

| Trigger | Skill | Path |
|---------|-------|------|
| frontend design | frontend-design | C:\Users\Javier\.config\opencode\skills\frontend-design\SKILL.md |

## Compact Rules

### frontend-design
- Use active voice as default. A control should say exactly what happens when it's used: "Save changes," not "Submit." An action keeps the same name through the whole flow, so the button that says "Publish" produces a toast that says "Published." The vocabulary of an interface is the signposting for someone navigating the product. Cohesion and consistency are how people learn their way around.
- Treat failure and emptiness as moments for direction, not mood. Explain what went wrong and how to fix it, in the interface's voice rather than a person's. Errors don't apologize, and they are never vague about what happened. An empty screen is an invitation to act.
- Keep the register conversational and tuned: plain verbs, sentence case, no filler, with tone matched to the brand and the audience. Let each element do exactly one job. A label labels, an example demonstrates, and nothing quietly does double duty.

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| README.md | C:\Users\Javier\OneDrive - ubiobio.cl\Escritorio\webos\La-Ultima-Cuota-WEBOO\README.md | Project overview and quick start |
| docker-compose.yml | C:\Users\Javier\OneDrive - ubiobio.cl\Escritorio\webos\La-Ultima-Cuota-WEBOO\docker-compose.yml | Docker service definitions and networking |