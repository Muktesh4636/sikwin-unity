// Copyright (c) Jeroen van Pienbroek. All rights reserved.
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
// WebGL keyboard implementation using TouchScreenKeyboard so the browser/mobile keyboard opens when
// the user taps ID or password (and other) input fields in the WebGL build.

using UnityEngine;

namespace AdvancedInputFieldPlugin
{
	public class WebGLKeyboard : NativeKeyboard
	{
		private TouchScreenKeyboard touchScreenKeyboard;
		private string lastText = string.Empty;

		private void Start()
		{
			HardwareKeyboardConnected = false;
		}

		public override void ShowKeyboard(string text, int selectionStartPosition, int selectionEndPosition, NativeKeyboardConfiguration configuration)
		{
			lastText = text ?? string.Empty;
			TouchScreenKeyboardType keyboardType = MapKeyboardType(configuration.keyboardType);
			touchScreenKeyboard = TouchScreenKeyboard.Open(
				lastText,
				keyboardType,
				!configuration.autocorrection,
				configuration.lineType == LineType.MULTILINE_NEWLINE,
				configuration.secure,
				false,
				"",
				configuration.characterLimit > 0 ? configuration.characterLimit : 0
			);
			OnKeyboardShow();
			EnableUpdates();
		}

		public override void HideKeyboard()
		{
			if (touchScreenKeyboard != null)
			{
				touchScreenKeyboard.active = false;
				touchScreenKeyboard = null;
			}
			OnKeyboardHide();
			DisableUpdates();
		}

		public override void EnableUpdates()
		{
			enabled = true;
			InputMethodManager.ClearEventQueue();
		}

		public override void DisableUpdates()
		{
			enabled = false;
		}

		private void Update()
		{
			if (touchScreenKeyboard == null) return;

			string currentText = touchScreenKeyboard.text ?? string.Empty;
			if (currentText != lastText)
			{
				lastText = currentText;
				int len = currentText.Length;
				OnTextEditUpdate(currentText, len, len);
			}

			switch (touchScreenKeyboard.status)
			{
				case TouchScreenKeyboard.Status.Done:
					OnKeyboardDone();
					touchScreenKeyboard = null;
					OnKeyboardHide();
					DisableUpdates();
					break;
				case TouchScreenKeyboard.Status.Canceled:
					OnKeyboardCancel();
					touchScreenKeyboard = null;
					OnKeyboardHide();
					DisableUpdates();
					break;
				case TouchScreenKeyboard.Status.LostFocus:
					touchScreenKeyboard = null;
					OnKeyboardHide();
					DisableUpdates();
					break;
			}
		}

		private static TouchScreenKeyboardType MapKeyboardType(KeyboardType type)
		{
			switch (type)
			{
				case KeyboardType.EMAIL_ADDRESS: return TouchScreenKeyboardType.EmailAddress;
				case KeyboardType.URL: return TouchScreenKeyboardType.URL;
				case KeyboardType.NUMBER_PAD: return TouchScreenKeyboardType.NumberPad;
				case KeyboardType.PHONE_PAD: return TouchScreenKeyboardType.PhonePad;
				case KeyboardType.NUMBERS_AND_PUNCTUATION: return TouchScreenKeyboardType.NumbersAndPunctuation;
				case KeyboardType.ASCII_CAPABLE: return TouchScreenKeyboardType.ASCIICapable;
				case KeyboardType.DECIMAL_PAD: return TouchScreenKeyboardType.DecimalPad;
				default: return TouchScreenKeyboardType.Default;
			}
		}
	}
}
