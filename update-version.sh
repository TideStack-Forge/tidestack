old_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
mvn versions:set -DnewVersion=$1

# git add pom.xml 以及各个子目录下的pom.xml
git add pom.xml */pom.xml
git commit -m "update version from $old_version to $1"

# 到各个子目录下依次 add */pom.xml 并 commit
for dir in $(ls -d */)
do
  if [ -f "pom.xml" ]; then
    cd $dir
    # 循环子目录的目录
    for subdir in $(ls -d */)
    do
      if [ ! -f ${subdir}pom.xml ]; then continue; fi
      # find 所有 pom.xml 文件
      cd $subdir
      find . -name "pom.xml" | xargs git add
      git commit -m "update version from $old_version to $1"
      cd ..
    done
    cd ..
  fi
done