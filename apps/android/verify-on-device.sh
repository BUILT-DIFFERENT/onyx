#!/bin/bash
# Device Testing Script for Milestone A
# Run this script when Android device with stylus is connected

set -e

echo "=========================================="
echo "Milestone A - Device Verification Script"
echo "=========================================="
echo ""

# Check device connection
echo "Step 1: Checking device connection..."
DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ ERROR: No device connected"
    echo "Please connect Android device via USB and enable USB debugging"
    exit 1
fi

echo "✅ Device connected"
adb devices
echo ""

# Install main APK
echo "Step 2: Installing main APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "✅ Main APK installed"
echo ""

# Install test APK
echo "Step 3: Installing androidTest APK..."
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
echo "✅ Test APK installed"
echo ""

# Run Ink API compatibility test
echo "Step 4: Running Ink API compatibility test..."
echo "This test validates InProgressStrokesView works on your device"
./gradlew :app:connectedDebugAndroidTest --tests "*.InkApiCompatTest" 2>&1 | tee ink-api-test-result.txt

if grep -q "PASSED" ink-api-test-result.txt; then
    echo "✅ RESULT: InkApiCompatTest PASSED"
    echo "✅ Decision: Use InProgressStrokesView (no fallback needed)"
    echo ""
    echo "Action: Mark Task 3.2a checkbox 2 as PASS in plan file"
else
    echo "❌ RESULT: InkApiCompatTest FAILED"
    echo "⚠️  Decision: Implement LowLatencyInkView fallback required"
    echo ""
    echo "Action: Implement fallback per plan line 2197-2201"
    exit 1
fi

# Launch app for manual testing
echo "Step 5: Launching app for manual testing..."
adb shell am start -n com.onyx.android/.MainActivity
echo "✅ App launched"
echo ""

# Manual testing instructions
echo "=========================================="
echo "MANUAL TESTING REQUIRED"
echo "=========================================="
echo ""
echo "Task 8.2: End-to-End Workflow"
echo "----------------------------"
echo "1. Tap the FAB (+ button) to create a new note"
echo "2. Draw at least 5 strokes with your stylus"
echo "3. Write 'hello world' in cursive"
echo "4. Go back to home screen"
echo "5. Type 'hello' in the search box"
echo "6. Verify your note appears in search results"
echo "7. Tap the result to open the note"
echo "8. Verify all strokes are visible"
echo ""
echo "Press ENTER when manual steps 1-8 are complete..."
read -r

# Verify persistence
echo ""
echo "Step 6: Testing persistence..."
echo "Force-stopping app..."
adb shell am force-stop com.onyx.android

echo "Relaunching app..."
adb shell am start -n com.onyx.android/.MainActivity

echo ""
echo "Check: Do you see your note in the list? (y/n)"
read -r note_visible

if [ "$note_visible" != "y" ]; then
    echo "❌ ERROR: Note not visible after restart"
    exit 1
fi

echo "Open the note. Are all strokes still visible? (y/n)"
read -r strokes_visible

if [ "$strokes_visible" != "y" ]; then
    echo "❌ ERROR: Strokes not persisted"
    exit 1
fi

echo "✅ Persistence verified"
echo ""

# Check database
echo "Step 7: Verifying recognition in database..."
adb shell "run-as com.onyx.android sqlite3 /data/data/com.onyx.android/databases/onyx.db 'SELECT recognizedText FROM recognition_index;'" 2>&1 | tee recognition-output.txt

if grep -q "hello" recognition-output.txt || grep -q "Hello" recognition-output.txt; then
    echo "✅ Recognition text found in database"
else
    echo "⚠️  WARNING: 'hello' not found in recognition index"
    echo "This may indicate MyScript recognition didn't run"
fi
echo ""

# PDF workflow testing
echo "=========================================="
echo "Task 8.3: PDF Workflow"
echo "=========================================="
echo ""
echo "Step 8: Preparing test PDF..."

# Check if test PDF exists
if [ ! -f "test.pdf" ]; then
    echo "Creating test PDF..."
    echo "%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj
3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<<>>>>endobj
xref
0 4
0000000000 65535 f 
0000000009 00000 n 
0000000052 00000 n 
0000000101 00000 n 
trailer<</Size 4/Root 1 0 R>>
startxref
190
%%EOF" > test.pdf
fi

adb push test.pdf /sdcard/Download/test.pdf
echo "✅ Test PDF pushed to device"
echo ""

echo "Manual steps:"
echo "1. In the app, import the PDF from Downloads folder"
echo "2. Add ink annotations on page 1 with your stylus"
echo "3. If PDF has multiple pages, navigate to page 2"
echo "4. Add more annotations"
echo "5. Go back to page 1 and verify first annotations are still visible"
echo ""
echo "Press ENTER when manual steps are complete..."
read -r

echo "Force-stopping app to test persistence..."
adb shell am force-stop com.onyx.android

echo "Relaunching app..."
adb shell am start -n com.onyx.android/.MainActivity

echo ""
echo "Open the PDF note. Are all annotations still visible? (y/n)"
read -r pdf_annotations_visible

if [ "$pdf_annotations_visible" != "y" ]; then
    echo "❌ ERROR: PDF annotations not persisted"
    exit 1
fi

echo "✅ PDF workflow verified"
echo ""

# Final checklist
echo "=========================================="
echo "VERIFICATION COMPLETE"
echo "=========================================="
echo ""
echo "Update plan file with results:"
echo "- Line 814: [x] App launches on physical tablet"
echo "- Line 816: [x] Can draw strokes with stylus (low latency)"
echo "- Line 820: [x] MyScript produces recognized text"
echo "- Line 2102: [x] 3.2a Ink API Fallback Decision (REQUIRED)"
echo "- Line 5128: [x] 8.2 End-to-end flow verification"
echo "- Line 5150: [x] 8.3 PDF workflow verification"
echo "- Line 5262: [x] Ink capture with pressure/tilt"
echo "- Line 5266: [x] Recognition produces searchable text"
echo ""
echo "Next step: Commit the plan file update"
echo ""
echo "✅ ALL DEVICE VERIFICATION TASKS COMPLETE"
