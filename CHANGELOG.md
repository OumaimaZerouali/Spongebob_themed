<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Spongebob Theme Changelog

## [Unreleased]

## [1.3.1]
### Fixed
- A leftover grid frame count no longer switches auto-slice off; the field is greyed out while auto-slice is on

## [1.3.0]
### Changed
- Auto-slice is on by default; small square pixel-art strips are still read as a grid
- Row picker is a dropdown listing every row the sheet has, with its frame count
- Bar height defaults to Auto (16 px for pixel art, 32 px for big sprites)

## [1.2.0]
### Added
- Auto-slice for ripped sprite sheets: background removal, row selection, frame detection
- Bar height setting (16/20/24/32 px)

## [1.1.0]
### Added
- Pixel-art support: PNG sprite sheets with frame count and speed, crisp nearest-neighbour scaling
- Optional fill and track tiles for the bar
- `templates/` with specs, blank canvases and examples

## [1.0.0]
### Added
- Dark "Bikini Bottom" UI theme and matching editor color scheme
- Animated underwater progress bar with a runner that swims across while things load
- Settings page to use your own image or animated GIF as the runner
