#!/usr/bin/python
# encoding: utf-8

import os
import sys
import argparse
import md5

DEBUG = True
def log(x):
    if DEBUG == True:
        print str(x)

def convert2image(dir):
    if os.path.isdir(dir):
        file_list = os.listdir(dir)
        for file in file_list:
            if (file.endswith('.rgba')):
                log('file:' + file)
                width_height = file.split('_')[-1].split('.')[0].split('*')
                file_name = file.split('.')[0]
                log('file:' + file + " " + width_height[0] + "*" + width_height[1])
                os.system('convert -size ' + width_height[0] + 'x' + width_height[1] + ' -depth 8 '
                    + dir + '/' + file + ' ' + dir + '/' + file_name + '.png')
                os.remove(dir + '/' + file)
    else:
        log(str(dir) + " has no .rgba file")

def getmd5(filename):
    file_txt = open(filename,'rb').read()
    m = md5.new(file_txt)
    return m.hexdigest()

def findSameImage(dir):
    if os.path.isdir(dir):
        os.system('echo > ' + dir + '/Duplicate_files');
        all_size = {}
        for file in os.listdir(dir):
            real_path=os.path.join(dir,file)
            if os.path.isfile(real_path) == True:
                size = os.stat(real_path).st_size
                name_and_md5=[real_path,'']
                if size in all_size.keys():
                    new_md5 = getmd5(real_path)
                    if all_size[size][1]=='':
                        all_size[size][1]=getmd5(all_size[size][0])

                    if new_md5 in all_size[size]:
                        log('Duplicate file: ' + file + ' == ' + all_size[size][0].split('/')[-1])
                        os.system('echo ' + 'Duplicate file: ' + file + ' == ' + all_size[size][0].split('/')[-1]
                         + ' >> ' + dir + '/Duplicate_files')
                    else:
                        all_size[size].append(new_md5)
                else:
                    all_size[size]=name_and_md5

    else:
        log(str(dir) + " has no .rgba file")

def main(input_file, output_dir, min_bitmap_size, debug):
    reload(sys)
    sys.setdefaultencoding('utf-8')
    global DEBUG
    DEBUG = debug

    log("input_file:" + input_file)
    log("output_dir:" + output_dir)
    log("min_bitmap_size:" + str(min_bitmap_size))
    log("debug:" + str(debug))

    os.system('./AutoMemoryAnalysis.jar ' + '-i '+ input_file + ' -o ' + output_dir + ' -m ' + str(min_bitmap_size) + ' -d ' + str(debug))
    convert2image(output_dir)
    findSameImage(output_dir)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description = "Auto memory analysis tool")
    parser.add_argument("input_file", help = "The hprof file to be parsed.")
    parser.add_argument("output_dir", nargs='?', help = "[Optional] Output dir, current dir by default.", default=".")
    parser.add_argument("min_bitmap_size", nargs='?', help = "[Optional] The minimum bitmap size that to parse, 10000 bytes by default", default=10000)
    parser.add_argument("debug", nargs='?', help = "[Optional] Debug trigger, true by default", default=True)
    args = parser.parse_args()
    main(args.input_file, args.output_dir, args.min_bitmap_size, args.debug)
