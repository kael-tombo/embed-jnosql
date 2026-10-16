# Minimal Public Website Design

## Goal

Make the public landing page as simple as possible while retaining the product introduction and a direct installation path.

## Agreed scope

Keep only:

- A minimal header with the existing brand mark and one “Get Started” link to `#install`.
- The existing hero content, including the product introduction, stats, and code sample.
- One hero “Get Started” action that links to `#install`.
- The existing install section and its installation tabs and copy controls.

Remove the feature overview, playground, console preview, engine details, framework integration guide, positioning and philosophy sections, demos, and footer. Remove the hero’s playground, console, and GitHub actions because they are not part of the approved minimal path. Do not edit the README or console application.

## Implementation approach

The public site is a single `docs/index.html` document. Remove the non-retained sections and replace their navigation and hero actions with the agreed single path to installation. Remove inline JavaScript used exclusively by deleted sections, including feature tabs, framework tabs, playground presets/execution, and console-preview tabs. Keep code-copy support, install-tab switching, and hero stat animation because retained content uses them.

Prune styles only when they are exclusive to removed sections. Retain shared layout, typography, responsive rules, hero and install styling, and styles required by the minimal header and copy/install interactions. Keep the existing transparent logo and light/dark favicon references.

## Behavior and verification

- The page has exactly two content sections: hero and installation.
- The header and hero each link to the installation section; there are no links to removed section IDs.
- Installation tabs still switch panels, and copy controls still copy their code snippets.
- The hero, header, and installation section remain usable at desktop and mobile widths.
- Browser verification checks section presence/absence, anchors, install tab behavior, and copy behavior.
- `git diff --check` is clean.

## Constraints

- Preserve the current installation content and its dependency coordinates.
- Do not change product behavior, application code, or README content.
- Do not retain hidden sections or section-only interaction code as dead code.
