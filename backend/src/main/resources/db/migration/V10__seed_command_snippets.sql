INSERT INTO command_snippets (category, title, command, description, explanation, warning, is_destructive, beginner_mode) VALUES
-- Setup
('setup', 'Initialize a Git repository', 'git init', 'Create a new Git repository in the current directory', 'Creates a hidden .git folder that tracks all changes in your project', NULL, false, true),
('setup', 'Set your name', 'git config --global user.name "Your Name"', 'Configure the name attached to your commits', 'This name appears in all your commits across all repositories', NULL, false, true),
('setup', 'Set your email', 'git config --global user.email "you@example.com"', 'Configure the email attached to your commits', 'Use the same email as your GitHub account for proper attribution', NULL, false, true),
('setup', 'Check Git status', 'git status', 'See which files are modified, staged, or untracked', 'Shows the current state of your working directory and staging area', NULL, false, true),
('setup', 'Clone a repository', 'git clone https://github.com/USERNAME/REPO.git', 'Download a copy of a remote repository', 'Creates a local copy of the entire project including all history', NULL, false, true),

-- Branching
('branching', 'Create and switch to a new branch', 'git switch -c feature/new-feature', 'Create a new branch and switch to it', 'The -c flag creates the branch; switch is the modern alternative to checkout', NULL, false, true),
('branching', 'List all branches', 'git branch', 'Show all local branches', 'The branch with an asterisk (*) is your current branch', NULL, false, true),
('branching', 'List all branches including remote', 'git branch -a', 'Show local and remote-tracking branches', 'Helps you see what branches exist on the remote', NULL, false, false),
('branching', 'Switch to an existing branch', 'git switch main', 'Change to an existing branch', 'Your working directory files will change to match that branch', NULL, false, true),
('branching', 'Merge a branch into current', 'git merge feature/new-feature', 'Combine changes from another branch', 'Creates a merge commit; use --no-ff to always create a merge commit', NULL, false, true),
('branching', 'Delete a local branch', 'git branch -d feature/old-feature', 'Delete a branch that has been merged', 'The -d flag only deletes if the branch is fully merged', NULL, false, true),
('branching', 'Force delete a branch', 'git branch -D feature/abandoned', 'Force delete a branch regardless of merge status', 'Use when you want to discard a branch completely', 'This will permanently delete unmerged work', true, false),

-- Remote
('remote', 'Add a remote repository', 'git remote add origin https://github.com/USER/REPO.git', 'Connect your local repo to a remote one', 'Origin is the conventional name for your primary remote', NULL, false, true),
('remote', 'View remote URLs', 'git remote -v', 'Show the remote repositories and their URLs', 'Verifies your remote is configured correctly', NULL, false, true),
('remote', 'Push to main branch', 'git push -u origin main', 'Upload your commits to the remote main branch', 'The -u flag sets origin main as the default upstream', NULL, false, true),
('remote', 'Pull latest changes', 'git pull --rebase', 'Download changes and reapply your commits on top', 'Rebase keeps a cleaner history than a merge commit', NULL, false, true),
('remote', 'Fetch without merging', 'git fetch origin', 'Download remote changes without applying them', 'Safe way to see what has changed before merging', NULL, false, true),
('remote', 'Push a feature branch', 'git push -u origin feature/name', 'Upload a feature branch to the remote', 'Others can now see and collaborate on your branch', NULL, false, true),
('remote', 'Remove a remote', 'git remote remove origin', 'Disconnect a remote repository', 'Useful when switching from HTTPS to SSH', NULL, false, false),
('remote', 'Rename a remote', 'git remote rename origin upstream', 'Change the name of a remote', 'Common when forking: original becomes upstream', NULL, false, false),

-- Undo
('undo', 'Stage a file', 'git add filename.txt', 'Add a file to the staging area', 'Staging prepares changes for the next commit', NULL, false, true),
('undo', 'Stage all changes', 'git add .', 'Stage all modified and new files', 'Convenient but review what you are staging first', NULL, false, true),
('undo', 'Unstage a file', 'git restore --staged filename.txt', 'Remove a file from the staging area', 'The file changes are preserved; they are just not staged', NULL, false, true),
('undo', 'Discard file changes', 'git restore filename.txt', 'Revert a file to its last committed state', 'Permanently discards local changes to that file', 'This cannot be undone. Your local changes will be lost.', true, true),
('undo', 'Amend the last commit', 'git commit --amend -m "New message"', 'Change the last commit message or add files', 'Only use if you have not pushed the commit yet', NULL, false, false),
('undo', 'Create a commit', 'git commit -m "Describe your changes"', 'Save staged changes with a descriptive message', 'A commit is a snapshot of your project at a point in time', NULL, false, true),
('undo', 'View commit history', 'git log --oneline --graph --decorate', 'Show compact commit history with branching graph', 'The --graph flag shows branch/merge structure visually', NULL, false, true),
('undo', 'View recent commits', 'git log --oneline -10', 'Show the last 10 commits in compact format', 'Quick way to see recent activity', NULL, false, true),
('undo', 'Revert a commit', 'git revert abc1234', 'Create a new commit that undoes a previous commit', 'Safe for shared history: does not rewrite existing commits', NULL, false, true),
('undo', 'Stash changes temporarily', 'git stash push -m "work in progress"', 'Save uncommitted changes for later', 'Useful when you need to switch branches quickly', NULL, false, true),

-- Advanced
('advanced', 'View diff of staged changes', 'git diff --staged', 'Show what will be included in the next commit', 'Compares staging area against the last commit', NULL, false, false),
('advanced', 'Cherry-pick a commit', 'git cherry-pick abc1234', 'Apply a specific commit from another branch', 'Useful for moving bug fixes between branches', NULL, false, false),
('advanced', 'Interactive rebase', 'git rebase -i HEAD~5', 'Rewrite, squash, or reorder the last 5 commits', 'Opens an editor to manipulate commits', 'Rewriting shared history can cause serious problems for collaborators', true, false),
('advanced', 'Show a specific commit', 'git show abc1234', 'Display the changes in a specific commit', 'Shows the commit message, author, date, and diff', NULL, false, false),
('advanced', 'Clean untracked files', 'git clean -fd', 'Remove all untracked files and directories', 'Useful for getting back to a clean state', 'This permanently deletes untracked files. They cannot be recovered.', true, false),
('advanced', 'Force push', 'git push --force-with-lease', 'Push even if the remote has diverged', 'Safer than --force: fails if someone else pushed', 'Force pushing can overwrite others work. Use with extreme caution.', true, false),
('advanced', 'Bisect to find bugs', 'git bisect start', 'Binary search to find which commit introduced a bug', 'Mark good and bad commits; Git narrows down the culprit', NULL, false, false),
('advanced', 'Tag a release', 'git tag -a v1.0.0 -m "Release version 1.0.0"', 'Create an annotated tag for a release', 'Tags mark specific points in history as important', NULL, false, true);
