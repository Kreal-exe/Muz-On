// Draws Muz On's launcher icons the way Photo Organizer's and Music Organizer's icons are drawn:
// a gradient tile with a white mark, rendered by AppKit so every edge is crisp. The marks are
// drawn here as paths (SF Symbols may only be used in apps for Apple platforms). Needs a Mac;
// run by hand when the icons change:
//
//   clang -fobjc-arc -framework Cocoa tools/icon/make_icons.m -o "$TMPDIR/make_icons"
//   "$TMPDIR/make_icons" .
//
// It writes, for each variant, the two layers of an Android adaptive icon into
// app/src/main/res/drawable-nodpi (432 px = 108 dp at the highest density: a background and a
// foreground whose mark stays inside the 66 dp safe circle), and docs/store/icon-512.png plus
// docs/logo.png, the default icon as a full tile.
#import <Cocoa/Cocoa.h>

typedef NS_ENUM(NSInteger, Mark) { MarkBars, MarkNote, MarkWave };

static NSColor *RGB(CGFloat r, CGFloat g, CGFloat b) { return [NSColor colorWithSRGBRed:r green:g blue:b alpha:1]; }

static NSBitmapImageRep *Canvas(NSInteger size) {
    NSBitmapImageRep *bitmap = [[NSBitmapImageRep alloc] initWithBitmapDataPlanes:NULL pixelsWide:size pixelsHigh:size
        bitsPerSample:8 samplesPerPixel:4 hasAlpha:YES isPlanar:NO colorSpaceName:NSDeviceRGBColorSpace bytesPerRow:0 bitsPerPixel:0];
    [NSGraphicsContext saveGraphicsState];
    NSGraphicsContext.currentContext = [NSGraphicsContext graphicsContextWithBitmapImageRep:bitmap];
    return bitmap;
}

static void Save(NSBitmapImageRep *bitmap, NSString *path) {
    [NSGraphicsContext restoreGraphicsState];
    [[bitmap representationUsingType:NSBitmapImageFileTypePNG properties:@{}] writeToFile:path atomically:YES];
}

// The tile: a top-to-bottom gradient with a faint light from above, as on the Organizer icons.
static void Tile(NSRect rect, NSBezierPath *shape, NSArray<NSColor *> *colors) {
    NSGradient *gradient = colors.count == 3
        ? [[NSGradient alloc] initWithColorsAndLocations:colors[0], 0.0, colors[1], 0.5, colors[2], 1.0, nil]
        : [[NSGradient alloc] initWithStartingColor:colors[0] endingColor:colors[1]];
    [gradient drawInBezierPath:shape angle:-90];
    NSGradient *light = [[NSGradient alloc] initWithStartingColor:[NSColor colorWithWhite:1 alpha:0.16] endingColor:[NSColor colorWithWhite:1 alpha:0]];
    [light drawInBezierPath:shape relativeCenterPosition:NSMakePoint(0, 0.9)];
}

// The mark, centred in a box of side `box` around `center`.
static void DrawMark(Mark mark, NSPoint center, CGFloat box, NSColor *color) {
    [NSGraphicsContext saveGraphicsState];
    NSShadow *shadow = [NSShadow new];
    shadow.shadowColor = [NSColor colorWithWhite:0 alpha:0.18];
    shadow.shadowOffset = NSMakeSize(0, -box * 0.02);
    shadow.shadowBlurRadius = box * 0.05;
    [shadow set];
    [color set];
    CGFloat u = box / 100;
    if (mark == MarkBars) {
        // Four rounded bars of a level meter, the tallest second.
        CGFloat heights[] = {44, 92, 64, 30};
        CGFloat w = 15, gap = 10, total = 4 * w + 3 * gap;
        for (int i = 0; i < 4; i++) {
            CGFloat h = heights[i];
            NSRect bar = NSMakeRect(center.x + (-total / 2 + i * (w + gap)) * u, center.y - h / 2 * u, w * u, h * u);
            [[NSBezierPath bezierPathWithRoundedRect:bar xRadius:w / 2 * u yRadius:w / 2 * u] fill];
        }
    } else if (mark == MarkNote) {
        // Two beamed notes, a little smaller: the shape is taller than the bars.
        u *= 0.8;
        CGFloat x0 = center.x - 41 * u, y0 = center.y - 52 * u;
        NSBezierPath *path = [NSBezierPath bezierPath];
        NSAffineTransform *t = [NSAffineTransform transform];
        [[NSBezierPath bezierPathWithOvalInRect:NSMakeRect(x0, y0, 30 * u, 23 * u)] fill];
        [[NSBezierPath bezierPathWithOvalInRect:NSMakeRect(x0 + 52 * u, y0 + 10 * u, 30 * u, 23 * u)] fill];
        [[NSBezierPath bezierPathWithRect:NSMakeRect(x0 + 22 * u, y0 + 11 * u, 8 * u, 66 * u)] fill];
        [[NSBezierPath bezierPathWithRect:NSMakeRect(x0 + 74 * u, y0 + 21 * u, 8 * u, 66 * u)] fill];
        [path moveToPoint:NSMakePoint(x0 + 22 * u, y0 + 77 * u)];
        [path lineToPoint:NSMakePoint(x0 + 82 * u, y0 + 87 * u)];
        [path lineToPoint:NSMakePoint(x0 + 82 * u, y0 + 105 * u)];
        [path lineToPoint:NSMakePoint(x0 + 22 * u, y0 + 95 * u)];
        [path closePath];
        (void)t;
        [path fill];
    } else {
        // A point with three arcs: sound going out.
        u *= 0.82;
        NSPoint c = NSMakePoint(center.x - 30 * u, center.y);
        [[NSBezierPath bezierPathWithOvalInRect:NSMakeRect(c.x - 10 * u, c.y - 10 * u, 20 * u, 20 * u)] fill];
        for (int i = 1; i <= 3; i++) {
            NSBezierPath *arc = [NSBezierPath bezierPath];
            [arc appendBezierPathWithArcWithCenter:c radius:(8 + i * 20) * u startAngle:-48 endAngle:48];
            arc.lineWidth = 11 * u;
            arc.lineCapStyle = NSLineCapStyleRound;
            [arc stroke];
        }
    }
    [NSGraphicsContext restoreGraphicsState];
}

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        NSString *root = argc > 1 ? @(argv[1]) : @".";
        NSString *res = [root stringByAppendingPathComponent:@"app/src/main/res/drawable-nodpi"];
        [NSFileManager.defaultManager createDirectoryAtPath:res withIntermediateDirectories:YES attributes:nil error:nil];

        NSArray *orange = @[RGB(1.00, 0.50, 0.27), RGB(0.87, 0.24, 0.12)];
        NSArray *violet = @[RGB(0.62, 0.47, 1.00), RGB(0.40, 0.22, 0.88)];
        NSDictionary<NSString *, NSArray *> *backgrounds = @{
            @"orange": orange,
            @"violet": violet,
            @"pink": @[RGB(1.00, 0.38, 0.62), RGB(0.83, 0.10, 0.42)],
            @"teal": @[RGB(0.22, 0.82, 0.74), RGB(0.02, 0.55, 0.53)],
            @"blue": @[RGB(0.36, 0.72, 1.00), RGB(0.20, 0.30, 0.86)],
            @"black": @[RGB(0.24, 0.24, 0.28), RGB(0.05, 0.05, 0.07)],
            @"white": @[RGB(1.00, 1.00, 1.00), RGB(0.89, 0.90, 0.92)],
            @"sunset": @[RGB(1.00, 0.74, 0.22), RGB(0.98, 0.33, 0.25), RGB(0.76, 0.15, 0.62)],
        };
        const NSInteger size = 432;
        NSRect full = NSMakeRect(0, 0, size, size);
        for (NSString *name in backgrounds) {
            NSBitmapImageRep *bg = Canvas(size);
            Tile(full, [NSBezierPath bezierPathWithRect:full], backgrounds[name]);
            Save(bg, [res stringByAppendingPathComponent:[NSString stringWithFormat:@"ic_l_bg_%@.png", name]]);
        }
        // Marks: white on colour, orange on the white tile. 46 dp of the 108 dp layer.
        NSDictionary *marks = @{ @"bars": @(MarkBars), @"note": @(MarkNote), @"wave": @(MarkWave) };
        for (NSString *name in marks) {
            for (NSString *tint in @[@"white", @"orange"]) {
                if (![name isEqualToString:@"bars"] && [tint isEqualToString:@"orange"]) continue;
                NSBitmapImageRep *fg = Canvas(size);
                DrawMark([marks[name] integerValue], NSMakePoint(size / 2.0, size / 2.0), size * 46 / 108.0,
                         [tint isEqualToString:@"white"] ? NSColor.whiteColor : RGB(0.93, 0.33, 0.16));
                Save(fg, [res stringByAppendingPathComponent:[NSString stringWithFormat:@"ic_l_fg_%@_%@.png", name, tint]]);
            }
        }
        // The default icon as one full tile for the store (512) and the README (224).
        for (NSArray *spec in @[@[@512, @"docs/store/icon-512.png"], @[@224, @"docs/logo.png"]]) {
            NSInteger s = [spec[0] integerValue];
            NSBitmapImageRep *tile = Canvas(s);
            NSRect r = NSMakeRect(0, 0, s, s);
            NSBezierPath *shape = [spec[1] hasPrefix:@"docs/logo"] ? [NSBezierPath bezierPathWithRoundedRect:r xRadius:s * 0.22 yRadius:s * 0.22] : [NSBezierPath bezierPathWithRect:r];
            Tile(r, shape, violet);
            DrawMark(MarkBars, NSMakePoint(s / 2.0, s / 2.0), s * 0.52, NSColor.whiteColor);
            Save(tile, [root stringByAppendingPathComponent:spec[1]]);
        }
    }
    return 0;
}
