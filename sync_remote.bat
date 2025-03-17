# 丢弃本地所有修改
# git restore .
# git restore --staged .

# 获取当前分支名称
# current_branch=$(git branch --show-current)
# echo "当前分支名称: $current_branch"

# 将分支的远程内容更新到本地
# git reset --hard origin/$current_branch

git clean -fd && git reset --hard HEAD~2 && git pull --rebase