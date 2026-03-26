mergeInto(LibraryManager.library, {
    GoToHomepage: function() {
        // Always go to site root (home page), not profile. Use top to exit iframes.
        window.top.location.href = window.location.origin + '/';
    }
});
