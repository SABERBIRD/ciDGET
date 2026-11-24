# Creating the GitHub token

ciDGET reads your Actions statuses with a **fine-grained personal access token**. It can only *read*, and only the repositories you choose (plus public repositories, which anyone can read anyway).

1. Open <https://github.com/settings/personal-access-tokens/new>.
   (Or: your avatar → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**.)
2. **Token name**: `ciDGET`.
3. **Expiration**: choose a date, e.g. 1 year. ciDGET warns you 7 days before it expires.
4. **Resource owner**: your account. For an organization's repositories, choose the organization; its admins may have to approve the token.
5. **Repository access** → **Only select repositories** → pick the repositories you want in the widget.
6. **Permissions** → **Repository permissions**:
   - **Actions** → **Read-only**
   - **Metadata** → **Read-only** (GitHub adds it automatically)
   - **Contents** → **Read-only** (needed to know the latest commit of each branch exactly; without it the widget still works but marks statuses with `~` as approximate)
   Leave everything else at **No access**.
7. **Generate token** and copy it (`github_pat_…`). GitHub shows it only once.
8. In ciDGET there is no app icon. Long-press the widget → **Reconfigure** (or tap the repo name → `[⚙]`) → **Token** → **[ paste token from clipboard ]**. There is no input field: the app takes the token you copied on GitHub, checks it, saves it and clears the clipboard.
9. **Several accounts or organizations:** a fine-grained token covers one owner, so create one token per owner and add each one the same way (**Token** → **Add a token**). Each widget uses the token that can see its repo; the repo list in the widget (tap the repo name) shows the repos of all tokens.

## Changing or removing it

- **New repositories:** edit the token on GitHub (same page → the token → **Edit** → repository access). No change in the app is needed.
- **Expired or revoked token** (`! 401 token` in the widget): create a new one and paste it in the widget settings (long-press the widget → **Reconfigure**, or tap the repo name → `[⚙]` → **Token** → **Replace** on that token).
- **Remove:** widget settings → **Token** → **Delete** next to the token. The app also deletes its cached data. To revoke the token itself, delete it on GitHub too.
